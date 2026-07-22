package com.example.valueinsoftbackend.ai.audit;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiRateLimitRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AiRateLimitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically consumes one request from today's quota. On the first request
     * after deployment, successful usage rows already written today seed the
     * counter so an application restart cannot reset the effective limit.
     */
    public boolean tryConsumeDailyUserRequest(long companyId, long userId, int limit) {
        if (limit <= 0) {
            return true;
        }

        Integer consumed = jdbcTemplate.queryForObject("""
                WITH historical_usage AS (
                    SELECT COUNT(*)::INTEGER AS request_count
                    FROM public.ai_usage_log
                    WHERE company_id = :companyId
                      AND user_id = :userId
                      AND created_at >= CURRENT_DATE
                      AND created_at < CURRENT_DATE + INTERVAL '1 day'
                ), consumed AS (
                    INSERT INTO public.ai_user_daily_request_usage
                        (company_id, user_id, usage_date, request_count, updated_at)
                    SELECT :companyId, :userId, CURRENT_DATE, request_count + 1, CURRENT_TIMESTAMP
                    FROM historical_usage
                    ON CONFLICT (company_id, user_id, usage_date)
                    DO UPDATE SET
                        request_count = ai_user_daily_request_usage.request_count + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE ai_user_daily_request_usage.request_count < :limit
                    RETURNING request_count
                )
                SELECT COALESCE(MAX(request_count), 0)::INTEGER FROM consumed
                """, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("userId", userId)
                .addValue("limit", limit), Integer.class);
        return consumed != null && consumed > 0 && consumed <= limit;
    }
}
