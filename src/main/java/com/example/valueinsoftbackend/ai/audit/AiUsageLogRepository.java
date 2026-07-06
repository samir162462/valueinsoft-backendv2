package com.example.valueinsoftbackend.ai.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class AiUsageLogRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiUsageLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(long companyId,
                       long userId,
                       UUID conversationId,
                       String modelName,
                       int promptTokens,
                       int completionTokens,
                       int totalTokens,
                       int cachedPromptTokens,
                       BigDecimal estimatedCost,
                       BigDecimal estimatedCostUsd,
                       Long durationMs) {
        jdbcTemplate.update("""
                INSERT INTO public.ai_usage_log
                    (id, company_id, user_id, conversation_id, model_name, prompt_tokens,
                     completion_tokens, total_tokens, cached_prompt_tokens, estimated_cost,
                     estimated_cost_usd, duration_ms, created_at)
                VALUES
                    (:id, :companyId, :userId, :conversationId, :modelName, :promptTokens,
                     :completionTokens, :totalTokens, :cachedPromptTokens, :estimatedCost,
                     :estimatedCostUsd, :durationMs, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                // Typed binding: non-chat surfaces pass a null conversation id, and an
                // untyped null cannot be inferred as UUID by the Postgres driver.
                .addValue("conversationId", conversationId, java.sql.Types.OTHER)
                .addValue("modelName", modelName)
                .addValue("promptTokens", Math.max(0, promptTokens))
                .addValue("completionTokens", Math.max(0, completionTokens))
                .addValue("totalTokens", Math.max(0, totalTokens))
                .addValue("cachedPromptTokens", Math.max(0, cachedPromptTokens))
                .addValue("estimatedCost", estimatedCost == null ? BigDecimal.ZERO : estimatedCost)
                .addValue("estimatedCostUsd", estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd)
                .addValue("durationMs", durationMs)
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }

    public record AiUsageBillingAggregate(long companyId,
                                          String modelName,
                                          long totalTokens,
                                          BigDecimal billableCost,
                                          long requestCount) {
    }

    /** Aggregated client-billable AI usage per company and model for [from, to). */
    public java.util.List<AiUsageBillingAggregate> aggregateCompanyUsage(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.query("""
                SELECT company_id, model_name,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(estimated_cost), 0) AS billable_cost,
                       COUNT(*) AS request_count
                FROM public.ai_usage_log
                WHERE created_at >= :from AND created_at < :to
                GROUP BY company_id, model_name
                HAVING COALESCE(SUM(estimated_cost), 0) > 0
                ORDER BY company_id, model_name
                """, new MapSqlParameterSource()
                        .addValue("from", Timestamp.valueOf(from))
                        .addValue("to", Timestamp.valueOf(to)),
                (rs, rowNum) -> new AiUsageBillingAggregate(
                        rs.getLong("company_id"),
                        rs.getString("model_name"),
                        rs.getLong("total_tokens"),
                        rs.getBigDecimal("billable_cost"),
                        rs.getLong("request_count")
                ));
    }

    public long countUserRequestsSince(long companyId, long userId, LocalDateTime since) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.ai_usage_log
                WHERE company_id = :companyId
                  AND user_id = :userId
                  AND created_at >= :since
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("since", Timestamp.valueOf(since)), Long.class);
        return count == null ? 0L : count;
    }

    public long sumCompanyTokensSince(long companyId, LocalDateTime since) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(total_tokens), 0)
                FROM public.ai_usage_log
                WHERE company_id = :companyId
                  AND created_at >= :since
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("since", Timestamp.valueOf(since)), Long.class);
        return total == null ? 0L : total;
    }
}
