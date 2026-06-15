package com.example.valueinsoftbackend.whatsapp.repository;

import com.example.valueinsoftbackend.whatsapp.model.WhatsAppSettings;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class WhatsAppSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public WhatsAppSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<WhatsAppSettings> rowMapper = new RowMapper<>() {
        @Override
        public WhatsAppSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
            return WhatsAppSettings.builder()
                    .settingsId(rs.getLong("settings_id"))
                    .companyId(rs.getLong("company_id"))
                    .enabled(rs.getBoolean("enabled"))
                    .phoneNumberId(rs.getString("phone_number_id"))
                    .businessAccountId(rs.getString("business_account_id"))
                    .accessTokenEncrypted(rs.getString("access_token_encrypted"))
                    .defaultCountryCode(rs.getString("default_country_code"))
                    .graphApiVersion(rs.getString("graph_api_version"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .updatedAt(rs.getTimestamp("updated_at").toInstant())
                    .build();
        }
    };

    public WhatsAppSettings findByCompanyId(long companyId) {
        String sql = "SELECT * FROM public.whatsapp_settings WHERE company_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, rowMapper, companyId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void upsert(WhatsAppSettings settings) {
        String sql = "INSERT INTO public.whatsapp_settings (company_id, enabled, phone_number_id, business_account_id, access_token_encrypted, default_country_code, graph_api_version) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (company_id) DO UPDATE SET " +
                     "enabled = EXCLUDED.enabled, " +
                     "phone_number_id = EXCLUDED.phone_number_id, " +
                     "business_account_id = EXCLUDED.business_account_id, " +
                     "access_token_encrypted = COALESCE(EXCLUDED.access_token_encrypted, public.whatsapp_settings.access_token_encrypted), " +
                     "default_country_code = EXCLUDED.default_country_code, " +
                     "graph_api_version = EXCLUDED.graph_api_version, " +
                     "updated_at = NOW()";

        jdbcTemplate.update(sql,
                settings.getCompanyId(),
                settings.isEnabled(),
                settings.getPhoneNumberId(),
                settings.getBusinessAccountId(),
                settings.getAccessTokenEncrypted(),
                settings.getDefaultCountryCode(),
                settings.getGraphApiVersion());
    }
}
