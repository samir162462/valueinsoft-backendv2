package com.example.valueinsoftbackend.companyinsights.backfill;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import com.example.valueinsoftbackend.companyinsights.engine.CompanyInsightEngineService;
import com.example.valueinsoftbackend.companyinsights.kpi.CompanyKpiAggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Async, chunked, resumable, checkpointed backfill of KPI snapshots for a company.
 * The API only enqueues a checkpoint row; {@link CompanyInsightBackfillWorker} advances it
 * one date-chunk at a time so a 13-month backfill never runs on a request thread.
 */
@Service
@Slf4j
public class CompanyInsightBackfillService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CompanyKpiAggregationService aggregationService;
    private final CompanyInsightEngineService engineService;
    private final CompanyInsightProperties properties;

    public CompanyInsightBackfillService(NamedParameterJdbcTemplate jdbcTemplate,
                                         CompanyKpiAggregationService aggregationService,
                                         CompanyInsightEngineService engineService,
                                         CompanyInsightProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.aggregationService = aggregationService;
        this.engineService = engineService;
        this.properties = properties;
    }

    public long createBackfill(long companyId, LocalDate from, LocalDate to, Long requestedBy) {
        int chunkDays = Math.max(1, properties.getBackfillChunkDays());
        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        int chunksTotal = (int) Math.ceil(totalDays / (double) chunkDays);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("chunksTotal", chunksTotal)
                .addValue("requestedBy", requestedBy);
        Long id = jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.company_insight_backfill_checkpoint
                            (company_id, grain, cursor_date, range_from, range_to, chunk_status,
                             chunks_total, chunks_done, status, requested_by)
                        VALUES (:companyId, 'all', NULL, :from, :to, 'PENDING', :chunksTotal, 0, 'PENDING', :requestedBy)
                        RETURNING backfill_id
                        """,
                params, Long.class);
        log.info("Company insight backfill created id={} companyId={} range={}..{} chunks={}",
                id, companyId, from, to, chunksTotal);
        return id == null ? -1L : id;
    }

    public Optional<Map<String, Object>> status(long companyId, long backfillId) {
        var rows = jdbcTemplate.queryForList(
                """
                        SELECT backfill_id, company_id, status, chunk_status, chunks_total, chunks_done,
                               range_from, range_to, cursor_date, error
                        FROM public.company_insight_backfill_checkpoint
                        WHERE backfill_id = :id AND company_id = :companyId
                        """,
                new MapSqlParameterSource().addValue("id", backfillId).addValue("companyId", companyId));
        return rows.stream().findFirst();
    }

    /**
     * Process the next pending chunk of the oldest active backfill. Returns true if work was
     * done. Uses FOR UPDATE SKIP LOCKED so multiple instances never process the same row.
     */
    @Transactional
    public boolean processNextChunk() {
        Map<String, Object> row = lockNextActive();
        if (row == null) {
            return false;
        }
        long backfillId = ((Number) row.get("backfill_id")).longValue();
        int companyId = ((Number) row.get("company_id")).intValue();
        LocalDate rangeFrom = toLocalDate(row.get("range_from"));
        LocalDate rangeTo = toLocalDate(row.get("range_to"));
        LocalDate cursor = toLocalDate(row.get("cursor_date"));
        int chunkDays = Math.max(1, properties.getBackfillChunkDays());

        LocalDate chunkStart = cursor == null ? rangeFrom : cursor.plusDays(1);
        LocalDate chunkEnd = chunkStart.plusDays(chunkDays - 1L);
        if (chunkEnd.isAfter(rangeTo)) {
            chunkEnd = rangeTo;
        }

        try {
            aggregationService.aggregateCompanyRange(companyId, chunkStart, chunkEnd);
            boolean done = !chunkEnd.isBefore(rangeTo);
            jdbcTemplate.update(
                    """
                            UPDATE public.company_insight_backfill_checkpoint
                            SET cursor_date = :cursor,
                                chunks_done = chunks_done + 1,
                                chunk_status = CASE WHEN :done THEN 'DONE' ELSE 'RUNNING' END,
                                status = CASE WHEN :done THEN 'COMPLETED' ELSE 'RUNNING' END
                            WHERE backfill_id = :id
                            """,
                    new MapSqlParameterSource()
                            .addValue("cursor", chunkEnd)
                            .addValue("done", done)
                            .addValue("id", backfillId));
            if (done) {
                engineService.generateForCompany(companyId, rangeTo);
                log.info("Company insight backfill completed id={} companyId={}", backfillId, companyId);
            }
            return true;
        } catch (RuntimeException exception) {
            jdbcTemplate.update(
                    "UPDATE public.company_insight_backfill_checkpoint SET status = 'FAILED', error = :err WHERE backfill_id = :id",
                    new MapSqlParameterSource().addValue("err", truncate(exception.getMessage())).addValue("id", backfillId));
            log.warn("Company insight backfill chunk failed id={} companyId={} reason={}",
                    backfillId, companyId, exception.getMessage());
            return true;
        }
    }

    private Map<String, Object> lockNextActive() {
        var rows = jdbcTemplate.queryForList(
                """
                        SELECT backfill_id, company_id, range_from, range_to, cursor_date
                        FROM public.company_insight_backfill_checkpoint
                        WHERE status IN ('PENDING','RUNNING')
                        ORDER BY created_at ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """,
                new MapSqlParameterSource());
        return rows.stream().findFirst().orElse(null);
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(value.toString());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
