package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class DbFinancePostingRequest {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinancePostingRequest(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public FinancePostingRequestItem createPostingRequest(FinancePostingRequestCreateRequest request,
                                                          String requestHash,
                                                          String requestPayloadJson,
                                                          Integer actorUserId) {
        UUID postingRequestId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_posting_request " +
                        "(company_id, branch_id, source_module, source_type, source_id, posting_date, fiscal_period_id, " +
                        "request_hash, request_payload, status, created_by, updated_by) " +
                        "VALUES (:companyId, :branchId, :sourceModule, :sourceType, :sourceId, :postingDate, :fiscalPeriodId, " +
                        ":requestHash, CAST(:requestPayload AS jsonb), 'pending', :actorUserId, :actorUserId) " +
                        "RETURNING posting_request_id",
                requestParams(request)
                        .addValue("requestHash", requestHash)
                        .addValue("requestPayload", requestPayloadJson)
                        .addValue("actorUserId", actorUserId),
                UUID.class);
        return getPostingRequestById(request.getCompanyId(), postingRequestId);
    }

    public FinancePostingRequestItem getPostingRequestById(int companyId, UUID postingRequestId) {
        return namedParameterJdbcTemplate.queryForObject(
                selectSql() +
                        "WHERE company_id = :companyId " +
                        "AND posting_request_id = :postingRequestId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingRequestId", postingRequestId),
                (rs, rowNum) -> mapPostingRequest(rs));
    }

    public FinancePostingRequestItem findPostingRequestBySource(int companyId,
                                                                String sourceModule,
                                                                String sourceType,
                                                                String sourceId) {
        ArrayList<FinancePostingRequestItem> requests = new ArrayList<>(namedParameterJdbcTemplate.query(
                selectSql() +
                        "WHERE company_id = :companyId " +
                        "AND source_module = :sourceModule " +
                        "AND source_type = :sourceType " +
                        "AND source_id = :sourceId " +
                        "ORDER BY created_at DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("sourceModule", sourceModule)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId),
                (rs, rowNum) -> mapPostingRequest(rs)));
        return requests.isEmpty() ? null : requests.getFirst();
    }

    public ArrayList<FinancePostingRequestItem> getPostingRequests(int companyId,
                                                                   Integer branchId,
                                                                   String status,
                                                                   String sourceModule,
                                                                   UUID fiscalPeriodId,
                                                                   int limit,
                                                                   int offset) {
        StringBuilder sql = new StringBuilder(selectSql())
                .append("WHERE company_id = :companyId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("status", status)
                .addValue("sourceModule", sourceModule)
                .addValue("fiscalPeriodId", fiscalPeriodId)
                .addValue("limit", limit)
                .addValue("offset", offset);

        if (branchId != null) {
            sql.append("AND branch_id = :branchId ");
        }
        if (status != null) {
            sql.append("AND status = :status ");
        }
        if (sourceModule != null) {
            sql.append("AND source_module = :sourceModule ");
        }
        if (fiscalPeriodId != null) {
            sql.append("AND fiscal_period_id = :fiscalPeriodId ");
        }

        sql.append("ORDER BY created_at DESC, posting_request_id DESC LIMIT :limit OFFSET :offset");
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> mapPostingRequest(rs)));
    }

    public int cancelPendingPostingRequest(int companyId,
                                           UUID postingRequestId,
                                           Integer actorUserId) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_posting_request " +
                        "SET status = 'cancelled', updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND posting_request_id = :postingRequestId " +
                        "AND status IN ('pending', 'failed')",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingRequestId", postingRequestId)
                        .addValue("actorUserId", actorUserId));
    }

    public int retryFailedPostingRequest(int companyId,
                                         UUID postingRequestId,
                                         Integer actorUserId) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_posting_request " +
                        "SET status = 'pending', last_error = NULL, updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND posting_request_id = :postingRequestId " +
                        "AND status = 'failed'",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingRequestId", postingRequestId)
                        .addValue("actorUserId", actorUserId));
    }

    public FinancePostingRequestItem claimPendingPostingRequest(int companyId,
                                                                UUID postingRequestId,
                                                                Integer actorUserId) {
        return singleOrNull(namedParameterJdbcTemplate.query(
                "UPDATE public.finance_posting_request " +
                        "SET status = 'processing', " +
                        "attempt_count = attempt_count + 1, " +
                        "last_attempt_at = NOW(), " +
                        "last_error = NULL, " +
                        "updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND posting_request_id = :postingRequestId " +
                        "AND status = 'pending' " +
                        "RETURNING posting_request_id, company_id, branch_id, posting_batch_id, source_module, source_type, " +
                        "source_id, posting_date, fiscal_period_id, request_hash, request_payload::text AS request_payload_json, " +
                        "status, attempt_count, last_attempt_at, last_error, journal_entry_id, created_at, created_by, " +
                        "updated_at, updated_by",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingRequestId", postingRequestId)
                        .addValue("actorUserId", actorUserId),
                (rs, rowNum) -> mapPostingRequest(rs)));
    }

    public FinancePostingRequestItem claimNextPendingPostingRequest(int companyId,
                                                                    String sourceModule,
                                                                    Integer actorUserId) {
        String sourceFilter = sourceModule == null ? "" : "AND source_module = :sourceModule ";
        return singleOrNull(namedParameterJdbcTemplate.query(
                "UPDATE public.finance_posting_request " +
                        "SET status = 'processing', " +
                        "attempt_count = attempt_count + 1, " +
                        "last_attempt_at = NOW(), " +
                        "last_error = NULL, " +
                        "updated_by = :actorUserId " +
                        "WHERE posting_request_id = ( " +
                        "SELECT posting_request_id " +
                        "FROM public.finance_posting_request " +
                        "WHERE company_id = :companyId " +
                        "AND status = 'pending' " +
                        sourceFilter +
                        "ORDER BY created_at ASC, posting_request_id ASC " +
                        "FOR UPDATE SKIP LOCKED " +
                        "LIMIT 1) " +
                        "AND company_id = :companyId " +
                        "RETURNING posting_request_id, company_id, branch_id, posting_batch_id, source_module, source_type, " +
                        "source_id, posting_date, fiscal_period_id, request_hash, request_payload::text AS request_payload_json, " +
                        "status, attempt_count, last_attempt_at, last_error, journal_entry_id, created_at, created_by, " +
                        "updated_at, updated_by",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("sourceModule", sourceModule)
                        .addValue("actorUserId", actorUserId),
                (rs, rowNum) -> mapPostingRequest(rs)));
    }

    public FinancePostingRequestItem markPostingRequestPosted(int companyId,
                                                              UUID postingRequestId,
                                                              UUID journalEntryId,
                                                              Integer actorUserId) {
        return singleOrNull(namedParameterJdbcTemplate.query(
                "UPDATE public.finance_posting_request " +
                        "SET status = 'posted', " +
                        "journal_entry_id = :journalEntryId, " +
                        "last_error = NULL, " +
                        "updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND posting_request_id = :postingRequestId " +
                        "AND status = 'processing' " +
                        "RETURNING posting_request_id, company_id, branch_id, posting_batch_id, source_module, source_type, " +
                        "source_id, posting_date, fiscal_period_id, request_hash, request_payload::text AS request_payload_json, " +
                        "status, attempt_count, last_attempt_at, last_error, journal_entry_id, created_at, created_by, " +
                        "updated_at, updated_by",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingRequestId", postingRequestId)
                        .addValue("journalEntryId", journalEntryId)
                        .addValue("actorUserId", actorUserId),
                (rs, rowNum) -> mapPostingRequest(rs)));
    }

    public FinancePostingRequestItem markPostingRequestFailed(int companyId,
                                                              UUID postingRequestId,
                                                              String lastError,
                                                              Integer actorUserId) {
        return singleOrNull(namedParameterJdbcTemplate.query(
                "UPDATE public.finance_posting_request " +
                        "SET status = 'failed', " +
                        "last_error = :lastError, " +
                        "updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND posting_request_id = :postingRequestId " +
                        "AND status = 'processing' " +
                        "RETURNING posting_request_id, company_id, branch_id, posting_batch_id, source_module, source_type, " +
                        "source_id, posting_date, fiscal_period_id, request_hash, request_payload::text AS request_payload_json, " +
                        "status, attempt_count, last_attempt_at, last_error, journal_entry_id, created_at, created_by, " +
                        "updated_at, updated_by",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingRequestId", postingRequestId)
                        .addValue("lastError", truncateError(lastError))
                        .addValue("actorUserId", actorUserId),
                (rs, rowNum) -> mapPostingRequest(rs)));
    }

    private MapSqlParameterSource requestParams(FinancePostingRequestCreateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("branchId", request.getBranchId())
                .addValue("sourceModule", request.getSourceModule())
                .addValue("sourceType", request.getSourceType())
                .addValue("sourceId", request.getSourceId())
                .addValue("postingDate", request.getPostingDate())
                .addValue("fiscalPeriodId", request.getFiscalPeriodId());
    }

    private String selectSql() {
        return "SELECT posting_request_id, company_id, branch_id, posting_batch_id, source_module, source_type, " +
                "source_id, posting_date, fiscal_period_id, request_hash, request_payload::text AS request_payload_json, " +
                "status, attempt_count, last_attempt_at, last_error, journal_entry_id, created_at, created_by, " +
                "updated_at, updated_by FROM public.finance_posting_request ";
    }

    private FinancePostingRequestItem mapPostingRequest(ResultSet rs) throws SQLException {
        return new FinancePostingRequestItem(
                rs.getObject("posting_request_id", UUID.class),
                rs.getInt("company_id"),
                nullableInteger(rs, "branch_id"),
                rs.getObject("posting_batch_id", UUID.class),
                rs.getString("source_module"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                localDate(rs, "posting_date"),
                rs.getObject("fiscal_period_id", UUID.class),
                rs.getString("request_hash"),
                rs.getString("request_payload_json"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                instant(rs, "last_attempt_at"),
                rs.getString("last_error"),
                rs.getObject("journal_entry_id", UUID.class),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by"));
    }

    private FinancePostingRequestItem singleOrNull(List<FinancePostingRequestItem> items) {
        return items.isEmpty() ? null : items.get(0);
    }

    private String truncateError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
