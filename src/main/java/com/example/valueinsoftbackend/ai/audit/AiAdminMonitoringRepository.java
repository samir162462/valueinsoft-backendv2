package com.example.valueinsoftbackend.ai.audit;

import com.example.valueinsoftbackend.ai.dto.AiAdminErrorDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminToolAuditItemDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminTopQuestionDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminUsageCompanyDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AiAdminMonitoringRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiAdminMonitoringRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiAdminUsageCompanyDto> findUsageByCompany(LocalDateTime from,
                                                           LocalDateTime to,
                                                           long nearLimitThreshold,
                                                           int limit) {
        return jdbcTemplate.query("""
                SELECT company_id,
                       COUNT(*) AS request_count,
                       COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
                       COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
                       COALESCE(SUM(total_tokens), 0) AS total_tokens,
                       COALESCE(SUM(estimated_cost), 0) AS estimated_cost,
                       COALESCE(SUM(estimated_cost_usd), 0) AS estimated_cost_usd,
                       COALESCE(AVG(duration_ms), 0) AS average_latency_ms
                FROM public.ai_usage_log
                WHERE created_at >= :fromDate
                  AND created_at < :toDate
                GROUP BY company_id
                ORDER BY request_count DESC, total_tokens DESC
                LIMIT :limit
                """, rangeParams(from, to).addValue("limit", limit), usageMapper(nearLimitThreshold));
    }

    public List<AiAdminToolAuditItemDto> findToolAudit(LocalDateTime from, LocalDateTime to, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, company_id, branch_id, user_id, tool_name,
                       output_summary, success, error_message, duration_ms, created_at
                FROM public.ai_tool_audit
                WHERE created_at >= :fromDate
                  AND created_at < :toDate
                ORDER BY created_at DESC
                LIMIT :limit
                """, rangeParams(from, to).addValue("limit", limit), toolAuditMapper());
    }

    public List<AiAdminErrorDto> findErrors(LocalDateTime from, LocalDateTime to, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, company_id, branch_id, user_id, tool_name,
                       error_message, duration_ms, created_at
                FROM public.ai_tool_audit
                WHERE created_at >= :fromDate
                  AND created_at < :toDate
                  AND success = false
                ORDER BY created_at DESC
                LIMIT :limit
                """, rangeParams(from, to).addValue("limit", limit), errorMapper());
    }

    public List<AiAdminTopQuestionDto> findTopQuestions(LocalDateTime from, LocalDateTime to, int limit) {
        return jdbcTemplate.query("""
                SELECT LEFT(REGEXP_REPLACE(TRIM(content), '\\s+', ' ', 'g'), 180) AS question,
                       COUNT(*) AS question_count,
                       MAX(created_at) AS last_asked_at
                FROM public.ai_message
                WHERE created_at >= :fromDate
                  AND created_at < :toDate
                  AND role = 'USER'
                  AND content IS NOT NULL
                  AND LENGTH(TRIM(content)) > 0
                GROUP BY LOWER(LEFT(REGEXP_REPLACE(TRIM(content), '\\s+', ' ', 'g'), 180)),
                         LEFT(REGEXP_REPLACE(TRIM(content), '\\s+', ' ', 'g'), 180)
                ORDER BY question_count DESC, last_asked_at DESC
                LIMIT :limit
                """, rangeParams(from, to).addValue("limit", limit), (rs, rowNum) -> new AiAdminTopQuestionDto(
                rs.getString("question"),
                rs.getLong("question_count"),
                rs.getTimestamp("last_asked_at").toInstant()
        ));
    }

    private MapSqlParameterSource rangeParams(LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource()
                .addValue("fromDate", Timestamp.valueOf(from))
                .addValue("toDate", Timestamp.valueOf(to));
    }

    private RowMapper<AiAdminUsageCompanyDto> usageMapper(long nearLimitThreshold) {
        return (rs, rowNum) -> {
            long totalTokens = rs.getLong("total_tokens");
            return new AiAdminUsageCompanyDto(
                    rs.getLong("company_id"),
                    rs.getLong("request_count"),
                    rs.getLong("prompt_tokens"),
                    rs.getLong("completion_tokens"),
                    totalTokens,
                    defaultDecimal(rs.getBigDecimal("estimated_cost")),
                    defaultDecimal(rs.getBigDecimal("estimated_cost_usd")),
                    defaultDecimal(rs.getBigDecimal("average_latency_ms")),
                    nearLimitThreshold > 0 && totalTokens >= nearLimitThreshold
            );
        };
    }

    private RowMapper<AiAdminToolAuditItemDto> toolAuditMapper() {
        return (rs, rowNum) -> new AiAdminToolAuditItemDto(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getLong("company_id"),
                nullableLong(rs.getObject("branch_id")),
                rs.getLong("user_id"),
                rs.getString("tool_name"),
                rs.getString("output_summary"),
                rs.getBoolean("success"),
                rs.getString("error_message"),
                nullableLong(rs.getObject("duration_ms")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private RowMapper<AiAdminErrorDto> errorMapper() {
        return (rs, rowNum) -> new AiAdminErrorDto(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getLong("company_id"),
                nullableLong(rs.getObject("branch_id")),
                rs.getLong("user_id"),
                rs.getString("tool_name"),
                rs.getString("error_message"),
                nullableLong(rs.getObject("duration_ms")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
