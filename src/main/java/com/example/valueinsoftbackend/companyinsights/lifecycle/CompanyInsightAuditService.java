package com.example.valueinsoftbackend.companyinsights.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes the Company Smart Insights lifecycle / admin-op audit trail
 * ({@code public.company_insight_audit_log}).
 */
@Service
@Slf4j
public class CompanyInsightAuditService {

    private final JdbcTemplate jdbcTemplate;

    public CompanyInsightAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(long companyId, Long insightId, Long userId, String eventType,
                    String inputJson, boolean success, long durationMs) {
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO public.company_insight_audit_log
                                (company_id, insight_id, user_id, event_type, input_json, success, duration_ms)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    companyId, insightId, userId, eventType, inputJson, success, durationMs
            );
        } catch (RuntimeException exception) {
            // Auditing must never break the primary operation.
            log.warn("Company insight audit write failed companyId={} event={} reason={}",
                    companyId, eventType, exception.getMessage());
        }
    }
}
