package com.example.valueinsoftbackend.whatsapp.repository;

import com.example.valueinsoftbackend.whatsapp.model.WhatsAppMessageLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class WhatsAppMessageLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public WhatsAppMessageLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<WhatsAppMessageLog> rowMapper = new RowMapper<>() {
        @Override
        public WhatsAppMessageLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return WhatsAppMessageLog.builder()
                    .logId(rs.getLong("log_id"))
                    .companyId(rs.getLong("company_id"))
                    .recipientPhone(rs.getString("recipient_phone"))
                    .messageType(rs.getString("message_type"))
                    .templateName(rs.getString("template_name"))
                    .languageCode(rs.getString("language_code"))
                    .messageBody(rs.getString("message_body"))
                    .providerMessageId(rs.getString("provider_message_id"))
                    .status(rs.getString("status"))
                    .errorCode(rs.getString("error_code"))
                    .errorMessage(rs.getString("error_message"))
                    .requestPayload(rs.getString("request_payload"))
                    .responsePayload(rs.getString("response_payload"))
                    .latencyMs(rs.getLong("latency_ms") == 0 && rs.wasNull() ? null : rs.getLong("latency_ms"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .updatedAt(rs.getTimestamp("updated_at").toInstant())
                    .build();
        }
    };

    public WhatsAppMessageLog insert(WhatsAppMessageLog log) {
        String sql = "INSERT INTO public.whatsapp_message_log (company_id, recipient_phone, message_type, template_name, language_code, message_body, provider_message_id, status, error_code, error_message, request_payload, response_payload, latency_ms) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                     
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, log.getCompanyId());
            ps.setString(2, log.getRecipientPhone());
            ps.setString(3, log.getMessageType());
            ps.setString(4, log.getTemplateName());
            ps.setString(5, log.getLanguageCode());
            ps.setString(6, log.getMessageBody());
            ps.setString(7, log.getProviderMessageId());
            ps.setString(8, log.getStatus());
            ps.setString(9, log.getErrorCode());
            ps.setString(10, log.getErrorMessage());
            ps.setString(11, log.getRequestPayload());
            ps.setString(12, log.getResponsePayload());
            if (log.getLatencyMs() != null) {
                ps.setLong(13, log.getLatencyMs());
            } else {
                ps.setNull(13, java.sql.Types.BIGINT);
            }
            return ps;
        }, keyHolder);

        if (keyHolder.getKeys() != null && keyHolder.getKeys().containsKey("log_id")) {
            log.setLogId(((Number) keyHolder.getKeys().get("log_id")).longValue());
        }
        return log;
    }

    public void updateStatus(long logId, String status, String providerMessageId, String errorCode, String errorMessage, String responsePayload, Long latencyMs) {
        String sql = "UPDATE public.whatsapp_message_log SET status = ?, provider_message_id = ?, error_code = ?, error_message = ?, response_payload = ?, latency_ms = ?, updated_at = NOW() WHERE log_id = ?";
        jdbcTemplate.update(sql, status, providerMessageId, errorCode, errorMessage, responsePayload, latencyMs, logId);
    }

    public List<WhatsAppMessageLog> findByCompanyId(long companyId, int offset, int limit) {
        String sql = "SELECT * FROM public.whatsapp_message_log WHERE company_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, companyId, limit, offset);
    }

    public long countByCompanyId(long companyId) {
        String sql = "SELECT COUNT(*) FROM public.whatsapp_message_log WHERE company_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, companyId);
        return count != null ? count : 0L;
    }
}
