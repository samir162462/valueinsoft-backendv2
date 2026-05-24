package com.example.valueinsoftbackend.ai.cache;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class AiInsightCacheService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AiProperties properties;

    public AiInsightCacheService(NamedParameterJdbcTemplate jdbcTemplate, AiProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Optional<String> get(AiSecurityContext context, Long branchId, String mode, String question) {
        String key = cacheKey(context.companyId(), branchId, mode, question);
        return getByKey(context, branchId, key, mode);
    }

    public Optional<String> getByKey(AiSecurityContext context, Long branchId, String cacheKey, String mode) {
        try {
            String answer = jdbcTemplate.queryForObject(
                    """
                            SELECT answer
                            FROM public.ai_insight_cache
                            WHERE company_id = :companyId
                              AND ((branch_id IS NULL AND :branchId IS NULL) OR branch_id = :branchId)
                              AND cache_key = :cacheKey
                              AND expires_at > now()
                            ORDER BY updated_at DESC
                            LIMIT 1
                            """,
                    params(context, branchId, cacheKey),
                    String.class
            );
            log.debug("AI insight cache hit companyId={} branchId={} mode={} key={} answerLength={}",
                    context.companyId(), branchId, mode, shortKey(cacheKey), answer == null ? 0 : answer.length());
            return Optional.ofNullable(answer);
        } catch (EmptyResultDataAccessException exception) {
            log.debug("AI insight cache miss companyId={} branchId={} mode={} key={}",
                    context.companyId(), branchId, mode, shortKey(cacheKey));
            return Optional.empty();
        }
    }

    public void put(AiSecurityContext context, Long branchId, String mode, String question, String answer) {
        if (answer == null || answer.isBlank()) {
            return;
        }
        String key = cacheKey(context.companyId(), branchId, mode, question);
        MapSqlParameterSource params = params(context, branchId, key)
                .addValue("question", normalizeQuestion(question))
                .addValue("answer", answer)
                .addValue("metadataJson", "{\"mode\":\"" + safeJson(mode) + "\"}")
                .addValue("expiresAt", OffsetDateTime.now().plusMinutes(Math.max(1, properties.getCacheTtlMinutes())));
        jdbcTemplate.update(
                """
                        INSERT INTO public.ai_insight_cache
                            (company_id, branch_id, cache_key, question, answer, metadata_json, expires_at)
                        VALUES
                            (:companyId, :branchId, :cacheKey, :question, :answer, :metadataJson, :expiresAt)
                        ON CONFLICT (company_id, branch_id, cache_key) DO UPDATE
                        SET answer = EXCLUDED.answer,
                            question = EXCLUDED.question,
                            metadata_json = EXCLUDED.metadata_json,
                            expires_at = EXCLUDED.expires_at,
                            updated_at = now()
                """,
                params
        );
        log.debug("AI insight cache stored companyId={} branchId={} mode={} key={} ttlMinutes={} answerLength={}",
                context.companyId(), branchId, mode, shortKey(key), properties.getCacheTtlMinutes(), answer.length());
    }

    public void putUntil(AiSecurityContext context,
                         Long branchId,
                         String cacheKey,
                         String question,
                         String answer,
                         String metadataJson,
                         OffsetDateTime expiresAt) {
        if (answer == null || answer.isBlank()) {
            return;
        }
        MapSqlParameterSource params = params(context, branchId, cacheKey)
                .addValue("question", normalizeQuestion(question))
                .addValue("answer", answer)
                .addValue("metadataJson", metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson)
                .addValue("expiresAt", expiresAt);
        jdbcTemplate.update(
                """
                        INSERT INTO public.ai_insight_cache
                            (company_id, branch_id, cache_key, question, answer, metadata_json, expires_at)
                        VALUES
                            (:companyId, :branchId, :cacheKey, :question, :answer, :metadataJson, :expiresAt)
                        ON CONFLICT (company_id, branch_id, cache_key) DO UPDATE
                        SET answer = EXCLUDED.answer,
                            question = EXCLUDED.question,
                            metadata_json = EXCLUDED.metadata_json,
                            expires_at = EXCLUDED.expires_at,
                            updated_at = now()
                """,
                params
        );
        log.debug("AI insight cache stored companyId={} branchId={} key={} expiresAt={} answerLength={}",
                context.companyId(), branchId, shortKey(cacheKey), expiresAt, answer.length());
    }

    public String dailyCacheKey(long companyId, Long branchId, String date, String locale) {
        return cacheKey(companyId, branchId, "DAILY_INSIGHTS", (date == null ? "" : date) + "|" + (locale == null ? "" : locale));
    }

    public String weeklyCacheKey(long companyId, Long branchId, String weekStartDate, String locale) {
        return cacheKey(companyId, branchId, "WEEKLY_INSIGHTS_V2", (weekStartDate == null ? "" : weekStartDate) + "|" + (locale == null ? "" : locale));
    }

    private MapSqlParameterSource params(AiSecurityContext context, Long branchId, String key) {
        return new MapSqlParameterSource()
                .addValue("companyId", context.companyId())
                .addValue("branchId", branchId)
                .addValue("cacheKey", key);
    }

    private String cacheKey(long companyId, Long branchId, String mode, String question) {
        String raw = companyId + "|" + branchId + "|" + normalizeMode(mode) + "|" + normalizeQuestion(question);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private String normalizeQuestion(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String normalizeMode(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).trim();
    }

    private String safeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String shortKey(String key) {
        return key == null || key.length() <= 12 ? key : key.substring(0, 12);
    }
}
