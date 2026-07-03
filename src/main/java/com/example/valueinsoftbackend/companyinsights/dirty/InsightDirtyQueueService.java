package com.example.valueinsoftbackend.companyinsights.dirty;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Debounced dirty tracking for inventory insights. Stock movements enqueue a
 * (company, product) marker with a delayed {@code process_after}; repeated movements within
 * the debounce window collapse onto the same unprocessed row (refreshing the delay). The
 * stock insight job later drains ready rows and recomputes only the affected companies.
 *
 * <p>This never recomputes anything inline — enqueue is O(1) and cheap.
 */
@Service
@Slf4j
public class InsightDirtyQueueService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CompanyInsightProperties properties;

    public InsightDirtyQueueService(NamedParameterJdbcTemplate jdbcTemplate,
                                    CompanyInsightProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /**
     * Mark a company/product dirty after a stock movement. Safe to call from movement code;
     * failures are swallowed so they never break the primary operation.
     */
    public void enqueue(long companyId, Long productId, String reason) {
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("companyId", companyId)
                    .addValue("productId", productId)
                    .addValue("reason", reason == null ? "STOCK_MOVEMENT" : reason)
                    .addValue("debounceMinutes", Math.max(0, properties.getDirtyDebounceMinutes()));
            jdbcTemplate.update(
                    """
                            INSERT INTO public.company_insight_dirty_queue
                                (company_id, product_id, reason, enqueued_at, process_after)
                            VALUES (:companyId, :productId, :reason, now(), now() + make_interval(mins => :debounceMinutes))
                            ON CONFLICT (company_id, product_id) WHERE processed_at IS NULL
                            DO UPDATE SET process_after = now() + make_interval(mins => :debounceMinutes),
                                          reason = EXCLUDED.reason,
                                          attempts = public.company_insight_dirty_queue.attempts + 1
                            """,
                    params
            );
        } catch (RuntimeException exception) {
            log.warn("Dirty-queue enqueue failed companyId={} productId={} reason={}",
                    companyId, productId, exception.getMessage());
        }
    }

    /** Distinct companies with ready (debounce elapsed) unprocessed dirty rows. */
    public List<Long> readyCompanies() {
        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT company_id
                        FROM public.company_insight_dirty_queue
                        WHERE processed_at IS NULL AND process_after <= now()
                        """,
                new MapSqlParameterSource(),
                Long.class
        );
    }

    /** Mark all currently-ready rows for a company as processed. */
    public int markCompanyProcessed(long companyId) {
        return jdbcTemplate.update(
                """
                        UPDATE public.company_insight_dirty_queue
                        SET processed_at = now()
                        WHERE company_id = :companyId AND processed_at IS NULL AND process_after <= now()
                        """,
                new MapSqlParameterSource("companyId", companyId)
        );
    }
}
