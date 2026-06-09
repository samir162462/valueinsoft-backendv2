package com.example.valueinsoftbackend.fx.repository;

import com.example.valueinsoftbackend.fx.model.FxRateSnapshotInsert;
import com.example.valueinsoftbackend.fx.model.FxRefreshTrigger;
import com.example.valueinsoftbackend.fx.model.GlobalFxRateSnapshot;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class GlobalFxRateRepository {

    private static final RowMapper<GlobalFxRateSnapshot> SNAPSHOT_MAPPER = (rs, rowNum) -> new GlobalFxRateSnapshot(
            rs.getLong("id"),
            rs.getString("base_currency"),
            rs.getString("target_currency"),
            rs.getObject("week_start_date", LocalDate.class),
            rs.getObject("effective_date", LocalDate.class),
            rs.getBigDecimal("rate"),
            rs.getString("rate_type"),
            rs.getString("source_code"),
            rs.getString("source_description"),
            rs.getBigDecimal("confidence"),
            rs.getObject("request_timestamp", OffsetDateTime.class),
            rs.getObject("response_timestamp", OffsetDateTime.class),
            rs.getString("raw_response"),
            rs.getString("status"),
            rs.getString("validation_status"),
            rs.getString("validation_message"),
            rs.getBoolean("is_initial_rate"),
            rs.getBoolean("is_scheduled_rate"),
            FxRefreshTrigger.valueOf(rs.getString("trigger_type")),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GlobalFxRateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean validRateExists(String baseCurrency, String targetCurrency) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.global_fx_rate_snapshot
                    WHERE base_currency = :baseCurrency
                      AND target_currency = :targetCurrency
                      AND status = 'VALID'
                      AND validation_status = 'VALID'
                )
                """,
                pairParams(baseCurrency, targetCurrency),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean scheduledValidRateExistsForDate(String baseCurrency,
                                                   String targetCurrency,
                                                   LocalDate effectiveDate,
                                                   String sourceCode) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.global_fx_rate_snapshot
                    WHERE base_currency = :baseCurrency
                      AND target_currency = :targetCurrency
                      AND effective_date = :effectiveDate
                      AND source_code = :sourceCode
                      AND is_scheduled_rate = TRUE
                      AND status = 'VALID'
                      AND validation_status = 'VALID'
                )
                """,
                pairParams(baseCurrency, targetCurrency)
                        .addValue("effectiveDate", Date.valueOf(effectiveDate))
                        .addValue("sourceCode", sourceCode),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public Optional<GlobalFxRateSnapshot> findLatestValid(String baseCurrency, String targetCurrency) {
        List<GlobalFxRateSnapshot> rows = jdbcTemplate.query(
                """
                SELECT *
                FROM public.global_fx_rate_snapshot
                WHERE base_currency = :baseCurrency
                  AND target_currency = :targetCurrency
                  AND status = 'VALID'
                  AND validation_status = 'VALID'
                ORDER BY request_timestamp DESC, id DESC
                LIMIT 1
                """,
                pairParams(baseCurrency, targetCurrency),
                SNAPSHOT_MAPPER
        );
        return rows.stream().findFirst();
    }

    public GlobalFxRateSnapshot insertSnapshot(FxRateSnapshotInsert snapshot) {
        String sql = """
                INSERT INTO public.global_fx_rate_snapshot (
                    base_currency, target_currency, week_start_date, effective_date,
                    rate, rate_type, source_code, source_description, confidence,
                    request_timestamp, response_timestamp, raw_response,
                    status, validation_status, validation_message,
                    is_initial_rate, is_scheduled_rate, trigger_type
                ) VALUES (
                    :baseCurrency, :targetCurrency, :weekStartDate, :effectiveDate,
                    :rate, :rateType, :sourceCode, :sourceDescription, :confidence,
                    :requestTimestamp, :responseTimestamp, :rawResponse,
                    :status, :validationStatus, :validationMessage,
                    :initialRate, :scheduledRate, :triggerType
                )
                RETURNING *
                """;

        return jdbcTemplate.queryForObject(sql, params(snapshot), SNAPSHOT_MAPPER);
    }

    private MapSqlParameterSource params(FxRateSnapshotInsert snapshot) {
        return new MapSqlParameterSource()
                .addValue("baseCurrency", snapshot.baseCurrency())
                .addValue("targetCurrency", snapshot.targetCurrency())
                .addValue("weekStartDate", Date.valueOf(snapshot.weekStartDate()))
                .addValue("effectiveDate", snapshot.effectiveDate() == null ? null : Date.valueOf(snapshot.effectiveDate()))
                .addValue("rate", snapshot.rate())
                .addValue("rateType", snapshot.rateType())
                .addValue("sourceCode", snapshot.sourceCode())
                .addValue("sourceDescription", snapshot.sourceDescription())
                .addValue("confidence", snapshot.confidence())
                .addValue("requestTimestamp", Timestamp.from(snapshot.requestTimestamp().toInstant()))
                .addValue("responseTimestamp", snapshot.responseTimestamp() == null ? null : Timestamp.from(snapshot.responseTimestamp().toInstant()))
                .addValue("rawResponse", snapshot.rawResponse())
                .addValue("status", snapshot.status())
                .addValue("validationStatus", snapshot.validationStatus())
                .addValue("validationMessage", snapshot.validationMessage())
                .addValue("initialRate", snapshot.initialRate())
                .addValue("scheduledRate", snapshot.scheduledRate())
                .addValue("triggerType", snapshot.triggerType().name());
    }

    private MapSqlParameterSource pairParams(String baseCurrency, String targetCurrency) {
        return new MapSqlParameterSource()
                .addValue("baseCurrency", normalizeCurrency(baseCurrency))
                .addValue("targetCurrency", normalizeCurrency(targetCurrency));
    }

    private String normalizeCurrency(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
