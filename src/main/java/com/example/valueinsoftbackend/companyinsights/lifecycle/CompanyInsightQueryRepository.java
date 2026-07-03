package com.example.valueinsoftbackend.companyinsights.lifecycle;

import com.example.valueinsoftbackend.companyinsights.api.dto.InsightDto;
import com.google.gson.Gson;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read access to {@code public.ai_company_insight} for the Admin API. All queries are
 * company-scoped from the resolved security context (never client input).
 */
@Repository
public class CompanyInsightQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();

    public CompanyInsightQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record InsightQuery(
            long companyId,
            List<String> statuses,   // null/empty -> active (NEW, SEEN)
            String role,             // null -> PRIMARY
            String severity,
            String category,
            String insightType,
            Long branchId,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize
    ) {
    }

    public long count(InsightQuery q) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM public.ai_company_insight WHERE company_id = :companyId");
        MapSqlParameterSource params = baseParams(sql, q);
        Long total = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return total == null ? 0L : total;
    }

    public List<InsightDto> list(InsightQuery q) {
        StringBuilder sql = new StringBuilder("SELECT * FROM public.ai_company_insight WHERE company_id = :companyId");
        MapSqlParameterSource params = baseParams(sql, q);
        sql.append(" ORDER BY priority_score DESC, last_detected_at DESC LIMIT :limit OFFSET :offset");
        int pageSize = Math.min(Math.max(1, q.pageSize()), 100);
        int page = Math.max(0, q.page());
        params.addValue("limit", pageSize).addValue("offset", (long) page * pageSize);
        return jdbcTemplate.query(sql.toString(), params, this::map);
    }

    public Optional<InsightDto> findById(long companyId, long id) {
        List<InsightDto> rows = jdbcTemplate.query(
                "SELECT * FROM public.ai_company_insight WHERE company_id = :companyId AND id = :id",
                new MapSqlParameterSource().addValue("companyId", companyId).addValue("id", id),
                this::map
        );
        return rows.stream().findFirst();
    }

    public Map<String, Long> activeSeverityCounts(long companyId) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        result.put("CRITICAL", 0L);
        result.put("WARNING", 0L);
        result.put("INFO", 0L);
        jdbcTemplate.query(
                """
                        SELECT severity, COUNT(*) AS c
                        FROM public.ai_company_insight
                        WHERE company_id = :companyId AND status IN ('NEW','SEEN') AND role = 'PRIMARY'
                        GROUP BY severity
                        """,
                new MapSqlParameterSource("companyId", companyId),
                rs -> {
                    result.put(rs.getString("severity"), rs.getLong("c"));
                }
        );
        return result;
    }

    private MapSqlParameterSource baseParams(StringBuilder sql, InsightQuery q) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", q.companyId());

        if (q.statuses() == null || q.statuses().isEmpty()) {
            sql.append(" AND status IN ('NEW','SEEN')");
        } else {
            sql.append(" AND status IN (:statuses)");
            params.addValue("statuses", q.statuses());
        }

        sql.append(" AND role = :role");
        params.addValue("role", q.role() == null || q.role().isBlank() ? "PRIMARY" : q.role());

        if (q.severity() != null && !q.severity().isBlank()) {
            sql.append(" AND severity = :severity");
            params.addValue("severity", q.severity());
        }
        if (q.category() != null && !q.category().isBlank()) {
            sql.append(" AND category = :category");
            params.addValue("category", q.category());
        }
        if (q.insightType() != null && !q.insightType().isBlank()) {
            sql.append(" AND insight_type = :insightType");
            params.addValue("insightType", q.insightType());
        }
        if (q.branchId() != null) {
            sql.append(" AND :branchId = ANY(affected_branch_ids)");
            params.addValue("branchId", q.branchId());
        }
        if (q.from() != null) {
            sql.append(" AND period_end >= :fromDate");
            params.addValue("fromDate", q.from());
        }
        if (q.to() != null) {
            sql.append(" AND period_start <= :toDate");
            params.addValue("toDate", q.to());
        }
        return params;
    }

    private InsightDto map(ResultSet rs, int rowNum) throws SQLException {
        return new InsightDto(
                rs.getLong("id"),
                rs.getString("insight_type"),
                rs.getString("severity"),
                rs.getString("category"),
                rs.getString("status"),
                rs.getString("role"),
                rs.getInt("priority_score"),
                rs.getString("period_type"),
                asString(rs, "period_start"),
                asString(rs, "period_end"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("executive_summary"),
                rs.getBigDecimal("financial_impact"),
                toLongList(rs.getArray("affected_branch_ids")),
                toLongList(rs.getArray("affected_product_ids")),
                rs.getInt("occurrence_count"),
                asString(rs, "last_detected_at"),
                rs.getString("action_code"),
                parseJson(rs.getString("action_context")),
                rs.getString("data_quality_status"),
                rs.getString("enrichment_source"),
                parseJson(rs.getString("localized_json")),
                parseJson(rs.getString("slots_json")),
                parseJson(rs.getString("contributing_factors_json")),
                parseJson(rs.getString("source_metrics_json"))
        );
    }

    private String asString(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value.toString();
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return gson.fromJson(json, Object.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<Long> toLongList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        List<Long> result = new ArrayList<>();
        if (raw instanceof Long[] values) {
            for (Long value : values) {
                if (value != null) {
                    result.add(value);
                }
            }
        } else if (raw instanceof Object[] values) {
            for (Object value : values) {
                if (value instanceof Number number) {
                    result.add(number.longValue());
                }
            }
        }
        return result;
    }
}
