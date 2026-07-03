package com.example.valueinsoftbackend.companyinsights.lifecycle;

import com.example.valueinsoftbackend.companyinsights.engine.InsightCandidate;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.StringJoiner;

/**
 * Persists insights with an atomic, race-safe upsert keyed by the atomic
 * {@code (company_id, insight_type, insight_key, period_start)}.
 *
 * <p>Lifecycle rules are encoded in the ON CONFLICT clause:
 * <ul>
 *   <li>DISMISSED insights are never auto-reopened (only occurrence_count/last_detected refresh).</li>
 *   <li>RESOLVED insights reopen (-&gt; NEW) only if the cooldown has elapsed; otherwise they stay resolved.</li>
 *   <li>EXPIRED insights reopen on recurrence.</li>
 *   <li>NEW/SEEN insights keep their status and refresh content.</li>
 * </ul>
 */
@Repository
@Slf4j
public class CompanyInsightRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();

    public CompanyInsightRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Detect (insert-or-refresh) an insight. Returns the number of rows affected (1).
     */
    public int upsertDetected(InsightCandidate c, int ttlDays) {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(Math.max(1, ttlDays));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", c.companyId())
                .addValue("insightType", c.type().name())
                .addValue("insightKey", c.insightKey())
                .addValue("periodType", c.periodType())
                .addValue("periodStart", c.periodStart())
                .addValue("periodEnd", c.periodEnd())
                .addValue("severity", c.severityName())
                .addValue("category", c.categoryName())
                .addValue("priorityScore", c.priorityScore())
                .addValue("role", c.role() == null ? "PRIMARY" : c.role().name())
                .addValue("suppressedReason", c.suppressedReason())
                .addValue("correlationGroup", c.correlationGroup())
                .addValue("title", c.title())
                .addValue("description", c.description())
                .addValue("executiveSummary", c.executiveSummary())
                .addValue("localizedJson", toJson(c.localized()))
                .addValue("slotsJson", toJson(c.slots()))
                .addValue("financialImpact", c.financialImpact())
                .addValue("affectedBranchIds", toArrayLiteral(c.affectedBranchIds()))
                .addValue("affectedProductIds", toArrayLiteral(c.affectedProductIds()))
                .addValue("contributingFactorsJson", c.contributingFactors() == null ? null : gson.toJson(c.contributingFactors()))
                .addValue("actionCode", c.actionCode() == null ? null : c.actionCode().name())
                .addValue("actionContext", c.actionContext() == null ? null : gson.toJson(c.actionContext()))
                .addValue("sourceMetricsJson", toJson(c.sourceMetrics()))
                .addValue("dataQualityStatus", c.dataQualityStatus() == null ? "COMPLETE" : c.dataQualityStatus())
                .addValue("expiresAt", expiresAt);

        return jdbcTemplate.update(
                """
                        INSERT INTO public.ai_company_insight
                            (company_id, insight_type, insight_key, period_type, period_start, period_end,
                             severity, category, priority_score, role, suppressed_reason, correlation_group,
                             title, description, executive_summary, localized_json, slots_json,
                             financial_impact, affected_branch_ids, affected_product_ids, contributing_factors_json,
                             action_code, action_context, source_metrics_json, data_quality_status,
                             status, occurrence_count, first_detected_at, last_detected_at, expires_at)
                        VALUES
                            (:companyId, :insightType, :insightKey, :periodType, :periodStart, :periodEnd,
                             :severity, :category, :priorityScore, :role, :suppressedReason, :correlationGroup,
                             :title, :description, :executiveSummary, CAST(:localizedJson AS jsonb), CAST(:slotsJson AS jsonb),
                             :financialImpact, CAST(:affectedBranchIds AS bigint[]), CAST(:affectedProductIds AS bigint[]), CAST(:contributingFactorsJson AS jsonb),
                             :actionCode, CAST(:actionContext AS jsonb), CAST(:sourceMetricsJson AS jsonb), :dataQualityStatus,
                             'NEW', 1, now(), now(), :expiresAt)
                        ON CONFLICT (company_id, insight_type, insight_key, period_start) DO UPDATE SET
                             severity = EXCLUDED.severity,
                             priority_score = EXCLUDED.priority_score,
                             role = EXCLUDED.role,
                             suppressed_reason = EXCLUDED.suppressed_reason,
                             correlation_group = EXCLUDED.correlation_group,
                             title = EXCLUDED.title,
                             description = EXCLUDED.description,
                             executive_summary = EXCLUDED.executive_summary,
                             localized_json = EXCLUDED.localized_json,
                             slots_json = EXCLUDED.slots_json,
                             financial_impact = EXCLUDED.financial_impact,
                             affected_branch_ids = EXCLUDED.affected_branch_ids,
                             affected_product_ids = EXCLUDED.affected_product_ids,
                             contributing_factors_json = EXCLUDED.contributing_factors_json,
                             action_code = EXCLUDED.action_code,
                             action_context = EXCLUDED.action_context,
                             source_metrics_json = EXCLUDED.source_metrics_json,
                             data_quality_status = EXCLUDED.data_quality_status,
                             occurrence_count = public.ai_company_insight.occurrence_count + 1,
                             last_detected_at = now(),
                             status = CASE
                                 WHEN public.ai_company_insight.status = 'DISMISSED' THEN 'DISMISSED'
                                 WHEN public.ai_company_insight.status = 'RESOLVED'
                                      AND public.ai_company_insight.cooldown_until IS NOT NULL
                                      AND now() < public.ai_company_insight.cooldown_until THEN 'RESOLVED'
                                 WHEN public.ai_company_insight.status = 'RESOLVED' THEN 'NEW'
                                 WHEN public.ai_company_insight.status = 'EXPIRED' THEN 'NEW'
                                 ELSE public.ai_company_insight.status
                             END,
                             resolved_at = CASE
                                 WHEN public.ai_company_insight.status = 'RESOLVED'
                                      AND (public.ai_company_insight.cooldown_until IS NULL
                                           OR now() >= public.ai_company_insight.cooldown_until) THEN NULL
                                 ELSE public.ai_company_insight.resolved_at
                             END,
                             cooldown_until = CASE
                                 WHEN public.ai_company_insight.status = 'RESOLVED'
                                      AND (public.ai_company_insight.cooldown_until IS NULL
                                           OR now() >= public.ai_company_insight.cooldown_until) THEN NULL
                                 ELSE public.ai_company_insight.cooldown_until
                             END,
                             expires_at = CASE
                                 WHEN public.ai_company_insight.status = 'DISMISSED' THEN public.ai_company_insight.expires_at
                                 ELSE EXCLUDED.expires_at
                             END
                        """,
                params
        );
    }

    /**
     * Apply a user status transition (SEEN / DISMISSED / RESOLVED). On RESOLVED the cooldown
     * window is set so that recurrence during cooldown does not reopen the insight.
     */
    public int applyStatus(long companyId, long insightId, String newStatus, int cooldownHours) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("insightId", insightId)
                .addValue("status", newStatus)
                .addValue("cooldownHours", Math.max(0, cooldownHours));
        return jdbcTemplate.update(
                """
                        UPDATE public.ai_company_insight
                        SET status = :status,
                            seen_at = CASE WHEN :status = 'SEEN' AND seen_at IS NULL THEN now() ELSE seen_at END,
                            dismissed_at = CASE WHEN :status = 'DISMISSED' THEN now() ELSE dismissed_at END,
                            resolved_at = CASE WHEN :status = 'RESOLVED' THEN now() ELSE resolved_at END,
                            cooldown_until = CASE WHEN :status = 'RESOLVED'
                                                  THEN now() + make_interval(hours => :cooldownHours)
                                                  ELSE cooldown_until END
                        WHERE id = :insightId AND company_id = :companyId
                        """,
                params
        );
    }

    /**
     * Mark active insights whose expiry has passed as EXPIRED (retention/expiry job).
     */
    public int expireStale(long companyId) {
        return jdbcTemplate.update(
                """
                        UPDATE public.ai_company_insight
                        SET status = 'EXPIRED'
                        WHERE company_id = :companyId
                          AND status IN ('NEW', 'SEEN')
                          AND expires_at IS NOT NULL
                          AND expires_at < now()
                        """,
                new MapSqlParameterSource("companyId", companyId)
        );
    }

    /**
     * Apply a validated AI narrative to an existing insight (source of truth numbers unchanged).
     */
    public int applyAiEnrichment(long companyId, String insightType, String insightKey,
                                 java.time.LocalDate periodStart, java.util.Map<String, Object> localized,
                                 String executiveSummary, String model) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("insightType", insightType)
                .addValue("insightKey", insightKey)
                .addValue("periodStart", periodStart)
                .addValue("localizedJson", toJson(localized))
                .addValue("summary", executiveSummary)
                .addValue("model", model);
        return jdbcTemplate.update(
                """
                        UPDATE public.ai_company_insight
                        SET localized_json = CAST(:localizedJson AS jsonb),
                            executive_summary = :summary,
                            enrichment_source = 'AI',
                            ai_model = :model
                        WHERE company_id = :companyId AND insight_type = :insightType
                          AND insight_key = :insightKey AND period_start = :periodStart
                        """,
                params
        );
    }

    private String toJson(Object value) {
        return value == null ? "{}" : gson.toJson(value);
    }

    private String toArrayLiteral(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Long id : ids) {
            if (id != null) {
                joiner.add(String.valueOf(id));
            }
        }
        return joiner.toString();
    }
}
