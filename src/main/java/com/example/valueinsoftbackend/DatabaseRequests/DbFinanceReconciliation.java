package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationItemItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationRunItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationRunCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportItemRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
public class DbFinanceReconciliation {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceReconciliation(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public FinanceReconciliationRunItem createRun(FinanceReconciliationRunCreateRequest request,
                                                  Integer actorUserId) {
        UUID runId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_reconciliation_run " +
                        "(company_id, branch_id, reconciliation_type, period_start, period_end, status, " +
                        "started_by, started_at, created_by, updated_by) " +
                        "VALUES (:companyId, :branchId, :reconciliationType, :periodStart, :periodEnd, 'running', " +
                        ":actorUserId, NOW(), :actorUserId, :actorUserId) " +
                        "RETURNING reconciliation_run_id",
                params(request)
                        .addValue("actorUserId", actorUserId),
                UUID.class);
        return getRunById(request.getCompanyId(), runId);
    }

    public FinanceReconciliationRunItem getRunById(int companyId, UUID reconciliationRunId) {
        return namedParameterJdbcTemplate.queryForObject(
                runSelectSql() +
                        "WHERE company_id = :companyId " +
                        "AND reconciliation_run_id = :reconciliationRunId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId),
                (rs, rowNum) -> mapRun(rs));
    }

    public ArrayList<FinanceReconciliationRunItem> getRuns(int companyId,
                                                           Integer branchId,
                                                           String reconciliationType,
                                                           String status,
                                                           int limit,
                                                           int offset) {
        StringBuilder sql = new StringBuilder(runSelectSql())
                .append("WHERE company_id = :companyId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("reconciliationType", reconciliationType)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", offset);

        if (branchId != null) {
            sql.append("AND branch_id = :branchId ");
        }
        if (reconciliationType != null) {
            sql.append("AND reconciliation_type = :reconciliationType ");
        }
        if (status != null) {
            sql.append("AND status = :status ");
        }

        sql.append("ORDER BY created_at DESC, reconciliation_run_id DESC LIMIT :limit OFFSET :offset");
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> mapRun(rs)));
    }

    public int createUnmatchedLedgerItems(int companyId,
                                          UUID reconciliationRunId,
                                          Integer branchId,
                                          LocalDate periodStart,
                                          LocalDate periodEnd,
                                          List<String> mappingKeys,
                                          Integer actorUserId) {
        return namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_reconciliation_item " +
                        "(company_id, reconciliation_run_id, source_type, source_id, ledger_line_id, match_status, " +
                        "difference_amount, resolution_status, created_by, updated_by) " +
                        "SELECT l.company_id, :reconciliationRunId, " +
                        "LEFT(l.source_module || '.' || l.source_type, 64), l.source_id, l.journal_line_id, " +
                        "'unmatched_ledger', ABS(l.debit_amount - l.credit_amount), 'unresolved', :actorUserId, :actorUserId " +
                        "FROM public.finance_journal_line l " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                        "WHERE l.company_id = :companyId " +
                        "AND j.status = 'posted' " +
                        "AND l.posting_date BETWEEN :periodStart AND :periodEnd " +
                        "AND (:branchId IS NULL OR l.branch_id = :branchId) " +
                        "AND NOT EXISTS ( " +
                        "SELECT 1 FROM public.finance_reconciliation_item existing " +
                        "WHERE existing.company_id = l.company_id " +
                        "AND existing.reconciliation_run_id = :reconciliationRunId " +
                        "AND existing.ledger_line_id = l.journal_line_id) " +
                        "AND l.account_id IN ( " +
                        "SELECT account_id FROM public.finance_account_mapping " +
                        "WHERE company_id = :companyId " +
                        "AND mapping_key IN (:mappingKeys) " +
                        "AND status = 'active' " +
                        "AND effective_from <= :periodEnd " +
                        "AND (effective_to IS NULL OR effective_to >= :periodStart))",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId)
                        .addValue("branchId", branchId)
                        .addValue("periodStart", periodStart)
                        .addValue("periodEnd", periodEnd)
                        .addValue("mappingKeys", mappingKeys)
                        .addValue("actorUserId", actorUserId));
    }

    public ArrayList<FinanceReconciliationSourceItem> importSourceItems(int companyId,
                                                                        Integer branchId,
                                                                        String reconciliationType,
                                                                        String sourceSystem,
                                                                        List<FinanceReconciliationSourceImportItemRequest> items,
                                                                        List<String> rawPayloadJson,
                                                                        Integer actorUserId) {
        ArrayList<FinanceReconciliationSourceItem> imported = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            FinanceReconciliationSourceImportItemRequest item = items.get(index);
            UUID sourceItemId = namedParameterJdbcTemplate.queryForObject(
                    "INSERT INTO public.finance_reconciliation_source_item " +
                            "(company_id, branch_id, reconciliation_type, source_system, external_reference, " +
                            "source_date, amount, currency_code, description, raw_payload, status, created_by, updated_by) " +
                            "VALUES (:companyId, :branchId, :reconciliationType, :sourceSystem, :externalReference, " +
                            ":sourceDate, :amount, :currencyCode, :description, CAST(:rawPayload AS jsonb), 'imported', " +
                            ":actorUserId, :actorUserId) " +
                            "ON CONFLICT (company_id, reconciliation_type, source_system, external_reference) " +
                            "DO UPDATE SET branch_id = EXCLUDED.branch_id, " +
                            "source_date = EXCLUDED.source_date, amount = EXCLUDED.amount, " +
                            "currency_code = EXCLUDED.currency_code, description = EXCLUDED.description, " +
                            "raw_payload = EXCLUDED.raw_payload, status = 'imported', updated_by = :actorUserId " +
                            "RETURNING reconciliation_source_item_id",
                    new MapSqlParameterSource()
                            .addValue("companyId", companyId)
                            .addValue("branchId", branchId)
                            .addValue("reconciliationType", reconciliationType)
                            .addValue("sourceSystem", sourceSystem)
                            .addValue("externalReference", item.getExternalReference())
                            .addValue("sourceDate", item.getSourceDate())
                            .addValue("amount", item.getAmount())
                            .addValue("currencyCode", item.getCurrencyCode())
                            .addValue("description", item.getDescription())
                            .addValue("rawPayload", rawPayloadJson.get(index))
                            .addValue("actorUserId", actorUserId),
                    UUID.class);
            imported.add(getSourceItemById(companyId, sourceItemId));
        }
        return imported;
    }

    public ArrayList<FinanceReconciliationSourceItem> getSourceItems(int companyId,
                                                                     Integer branchId,
                                                                     String reconciliationType,
                                                                     String sourceSystem,
                                                                     String status,
                                                                     int limit,
                                                                     int offset) {
        StringBuilder sql = new StringBuilder(sourceItemSelectSql())
                .append("WHERE company_id = :companyId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("reconciliationType", reconciliationType)
                .addValue("sourceSystem", sourceSystem)
                .addValue("status", status)
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (branchId != null) {
            sql.append("AND branch_id = :branchId ");
        }
        if (reconciliationType != null) {
            sql.append("AND reconciliation_type = :reconciliationType ");
        }
        if (sourceSystem != null) {
            sql.append("AND source_system = :sourceSystem ");
        }
        if (status != null) {
            sql.append("AND status = :status ");
        }
        sql.append("ORDER BY source_date DESC, created_at DESC LIMIT :limit OFFSET :offset");
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> mapSourceItem(rs)));
    }

    public FinanceReconciliationSourceItem getSourceItemById(int companyId, UUID reconciliationSourceItemId) {
        return namedParameterJdbcTemplate.queryForObject(
                sourceItemSelectSql() +
                        "WHERE company_id = :companyId " +
                        "AND reconciliation_source_item_id = :reconciliationSourceItemId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationSourceItemId", reconciliationSourceItemId),
                (rs, rowNum) -> mapSourceItem(rs));
    }

    public int createMatchedSourceItems(int companyId,
                                        UUID reconciliationRunId,
                                        Integer branchId,
                                        String reconciliationType,
                                        LocalDate periodStart,
                                        LocalDate periodEnd,
                                        List<String> mappingKeys,
                                        Integer actorUserId) {
        return namedParameterJdbcTemplate.update(
                "WITH candidate_matches AS ( " +
                        "SELECT DISTINCT ON (s.reconciliation_source_item_id) " +
                        "s.reconciliation_source_item_id, s.external_reference, s.amount AS source_amount, " +
                        "l.journal_line_id, ABS(l.debit_amount - l.credit_amount) AS ledger_amount " +
                        "FROM public.finance_reconciliation_source_item s " +
                        "JOIN public.finance_journal_line l ON l.company_id = s.company_id " +
                        "AND l.posting_date BETWEEN :periodStart AND :periodEnd " +
                        "AND l.currency_code = s.currency_code " +
                        "AND (LOWER(l.payment_id) = s.external_reference OR LOWER(l.source_id) = s.external_reference) " +
                        "JOIN public.finance_journal_entry j " +
                        "ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id " +
                        "WHERE s.company_id = :companyId " +
                        "AND s.reconciliation_type = :reconciliationType " +
                        "AND s.source_date BETWEEN :periodStart AND :periodEnd " +
                        "AND (:branchId IS NULL OR s.branch_id = :branchId OR s.branch_id IS NULL) " +
                        "AND (:branchId IS NULL OR l.branch_id = :branchId) " +
                        "AND j.status = 'posted' " +
                        "AND l.account_id IN (SELECT account_id FROM public.finance_account_mapping " +
                        "WHERE company_id = :companyId AND mapping_key IN (:mappingKeys) AND status = 'active' " +
                        "AND effective_from <= :periodEnd AND (effective_to IS NULL OR effective_to >= :periodStart)) " +
                        "ORDER BY s.reconciliation_source_item_id, " +
                        "CASE WHEN s.amount = ABS(l.debit_amount - l.credit_amount) THEN 0 ELSE 1 END, l.posting_date ASC) " +
                        "INSERT INTO public.finance_reconciliation_item " +
                        "(company_id, reconciliation_run_id, reconciliation_source_item_id, source_type, source_id, ledger_line_id, " +
                        "match_status, difference_amount, resolution_status, created_by, updated_by) " +
                        "SELECT :companyId, :reconciliationRunId, reconciliation_source_item_id, " +
                        "'imported_source', external_reference, journal_line_id, " +
                        "CASE WHEN source_amount = ledger_amount THEN 'matched' ELSE 'difference' END, " +
                        "ABS(source_amount - ledger_amount), " +
                        "CASE WHEN source_amount = ledger_amount THEN 'resolved' ELSE 'unresolved' END, " +
                        ":actorUserId, :actorUserId FROM candidate_matches",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId)
                        .addValue("branchId", branchId)
                        .addValue("reconciliationType", reconciliationType)
                        .addValue("periodStart", periodStart)
                        .addValue("periodEnd", periodEnd)
                        .addValue("mappingKeys", mappingKeys)
                        .addValue("actorUserId", actorUserId));
    }

    public int createUnmatchedSourceItems(int companyId,
                                          UUID reconciliationRunId,
                                          Integer branchId,
                                          String reconciliationType,
                                          LocalDate periodStart,
                                          LocalDate periodEnd,
                                          Integer actorUserId) {
        return namedParameterJdbcTemplate.update(
                "INSERT INTO public.finance_reconciliation_item " +
                        "(company_id, reconciliation_run_id, reconciliation_source_item_id, source_type, source_id, " +
                        "match_status, difference_amount, resolution_status, created_by, updated_by) " +
                        "SELECT s.company_id, :reconciliationRunId, s.reconciliation_source_item_id, " +
                        "'imported_source', s.external_reference, 'unmatched_source', s.amount, 'unresolved', " +
                        ":actorUserId, :actorUserId FROM public.finance_reconciliation_source_item s " +
                        "WHERE s.company_id = :companyId AND s.reconciliation_type = :reconciliationType " +
                        "AND s.source_date BETWEEN :periodStart AND :periodEnd " +
                        "AND (:branchId IS NULL OR s.branch_id = :branchId OR s.branch_id IS NULL) " +
                        "AND NOT EXISTS (SELECT 1 FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = s.company_id AND i.reconciliation_run_id = :reconciliationRunId " +
                        "AND i.reconciliation_source_item_id = s.reconciliation_source_item_id)",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId)
                        .addValue("branchId", branchId)
                        .addValue("reconciliationType", reconciliationType)
                        .addValue("periodStart", periodStart)
                        .addValue("periodEnd", periodEnd)
                        .addValue("actorUserId", actorUserId));
    }

    public int refreshSourceItemStatusesForRun(int companyId,
                                               UUID reconciliationRunId,
                                               Integer actorUserId) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_reconciliation_source_item s " +
                        "SET status = CASE " +
                        "WHEN EXISTS (SELECT 1 FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = s.company_id " +
                        "AND i.reconciliation_run_id = :reconciliationRunId " +
                        "AND i.reconciliation_source_item_id = s.reconciliation_source_item_id " +
                        "AND i.match_status = 'matched') THEN 'matched' " +
                        "WHEN EXISTS (SELECT 1 FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = s.company_id " +
                        "AND i.reconciliation_run_id = :reconciliationRunId " +
                        "AND i.reconciliation_source_item_id = s.reconciliation_source_item_id " +
                        "AND i.match_status IN ('unmatched_source', 'difference')) THEN 'exception' " +
                        "ELSE s.status END, " +
                        "updated_by = :actorUserId " +
                        "WHERE s.company_id = :companyId " +
                        "AND EXISTS (SELECT 1 FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = s.company_id " +
                        "AND i.reconciliation_run_id = :reconciliationRunId " +
                        "AND i.reconciliation_source_item_id = s.reconciliation_source_item_id)",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId)
                        .addValue("actorUserId", actorUserId));
    }

    public FinanceReconciliationRunItem completeRun(int companyId,
                                                    UUID reconciliationRunId,
                                                    Integer actorUserId) {
        namedParameterJdbcTemplate.update(
                "UPDATE public.finance_reconciliation_run r " +
                        "SET difference_amount = COALESCE(( " +
                        "SELECT SUM(i.difference_amount) " +
                        "FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = r.company_id " +
                        "AND i.reconciliation_run_id = r.reconciliation_run_id " +
                        "AND i.resolution_status = 'unresolved'), 0), " +
                        "status = CASE WHEN EXISTS ( " +
                        "SELECT 1 FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = r.company_id " +
                        "AND i.reconciliation_run_id = r.reconciliation_run_id " +
                        "AND i.resolution_status = 'unresolved') " +
                        "THEN 'completed_with_exceptions' ELSE 'completed' END, " +
                        "completed_at = NOW(), " +
                        "updated_by = :actorUserId " +
                        "WHERE r.company_id = :companyId " +
                        "AND r.reconciliation_run_id = :reconciliationRunId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId)
                        .addValue("actorUserId", actorUserId));
        return getRunById(companyId, reconciliationRunId);
    }

    public ArrayList<FinanceReconciliationItemItem> getItems(int companyId,
                                                             UUID reconciliationRunId,
                                                             String matchStatus,
                                                             String resolutionStatus,
                                                             int limit,
                                                             int offset) {
        StringBuilder sql = new StringBuilder(itemSelectSql())
                .append("WHERE company_id = :companyId ")
                .append("AND reconciliation_run_id = :reconciliationRunId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("reconciliationRunId", reconciliationRunId)
                .addValue("matchStatus", matchStatus)
                .addValue("resolutionStatus", resolutionStatus)
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (matchStatus != null) {
            sql.append("AND match_status = :matchStatus ");
        }
        if (resolutionStatus != null) {
            sql.append("AND resolution_status = :resolutionStatus ");
        }
        sql.append("ORDER BY created_at DESC, reconciliation_item_id DESC LIMIT :limit OFFSET :offset");
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> mapItem(rs)));
    }

    public FinanceReconciliationItemItem getItemById(int companyId, UUID reconciliationItemId) {
        return namedParameterJdbcTemplate.queryForObject(
                itemSelectSql() +
                        "WHERE company_id = :companyId " +
                        "AND reconciliation_item_id = :reconciliationItemId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationItemId", reconciliationItemId),
                (rs, rowNum) -> mapItem(rs));
    }

    public FinanceReconciliationItemItem updateItemResolution(int companyId,
                                                              UUID reconciliationItemId,
                                                              String resolutionStatus,
                                                              String resolutionNote,
                                                              com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationItemResolutionRequest request,
                                                              Integer actorUserId) {
        UUID itemId = namedParameterJdbcTemplate.queryForObject(
                "UPDATE public.finance_reconciliation_item " +
                        "SET resolution_status = :resolutionStatus, " +
                        "resolution_note = :resolutionNote, " +
                        "resolution_proof_file_key = :proofFileKey, " +
                        "resolution_proof_file_name = :proofFileName, " +
                        "resolution_proof_file_type = :proofFileType, " +
                        "resolution_proof_file_size = :proofFileSize, " +
                        "resolution_proof_uploaded_at = CASE WHEN :proofFileKey IS NOT NULL THEN NOW() ELSE resolution_proof_uploaded_at END, " +
                        "resolution_proof_uploaded_by = CASE WHEN :proofFileKey IS NOT NULL THEN :actorUserId ELSE resolution_proof_uploaded_by END, " +
                        "updated_by = :actorUserId " +
                        "WHERE company_id = :companyId " +
                        "AND reconciliation_item_id = :reconciliationItemId " +
                        "RETURNING reconciliation_item_id",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationItemId", reconciliationItemId)
                        .addValue("resolutionStatus", resolutionStatus)
                        .addValue("resolutionNote", resolutionNote)
                        .addValue("proofFileKey", request.getProofFileKey())
                        .addValue("proofFileName", request.getProofFileName())
                        .addValue("proofFileType", request.getProofFileType())
                        .addValue("proofFileSize", request.getProofFileSize())
                        .addValue("actorUserId", actorUserId),
                UUID.class);
        return getItemById(companyId, itemId);
    }

    public void refreshRunDifference(int companyId, UUID reconciliationRunId, Integer actorUserId) {
        namedParameterJdbcTemplate.update(
                "UPDATE public.finance_reconciliation_run r " +
                        "SET difference_amount = COALESCE(( " +
                        "SELECT SUM(i.difference_amount) " +
                        "FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = r.company_id " +
                        "AND i.reconciliation_run_id = r.reconciliation_run_id " +
                        "AND i.resolution_status = 'unresolved'), 0), " +
                        "status = CASE WHEN EXISTS ( " +
                        "SELECT 1 FROM public.finance_reconciliation_item i " +
                        "WHERE i.company_id = r.company_id " +
                        "AND i.reconciliation_run_id = r.reconciliation_run_id " +
                        "AND i.resolution_status = 'unresolved') " +
                        "THEN 'completed_with_exceptions' ELSE 'completed' END, " +
                        "updated_by = :actorUserId " +
                        "WHERE r.company_id = :companyId " +
                        "AND r.reconciliation_run_id = :reconciliationRunId " +
                        "AND r.status IN ('completed', 'completed_with_exceptions')",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("reconciliationRunId", reconciliationRunId)
                        .addValue("actorUserId", actorUserId));
    }

    private MapSqlParameterSource params(FinanceReconciliationRunCreateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("branchId", request.getBranchId())
                .addValue("reconciliationType", request.getReconciliationType())
                .addValue("periodStart", request.getPeriodStart())
                .addValue("periodEnd", request.getPeriodEnd());
    }

    private String runSelectSql() {
        return "SELECT reconciliation_run_id, company_id, branch_id, reconciliation_type, period_start, period_end, " +
                "status, difference_amount, started_by, started_at, completed_at, created_at, created_by, updated_at, updated_by " +
                "FROM public.finance_reconciliation_run ";
    }

    private String itemSelectSql() {
        return "SELECT reconciliation_item_id, company_id, reconciliation_run_id, reconciliation_source_item_id, " +
                "source_type, source_id, ledger_line_id, " +
                "match_status, difference_amount, resolution_status, resolution_note, " +
                "resolution_proof_file_key, resolution_proof_file_name, resolution_proof_file_type, " +
                "resolution_proof_file_size, resolution_proof_uploaded_at, resolution_proof_uploaded_by, " +
                "created_at, created_by, updated_at, updated_by " +
                "FROM public.finance_reconciliation_item ";
    }

    private String sourceItemSelectSql() {
        return "SELECT reconciliation_source_item_id, company_id, branch_id, reconciliation_type, source_system, " +
                "external_reference, source_date, amount, currency_code, description, raw_payload::text AS raw_payload_json, " +
                "status, created_at, created_by, updated_at, updated_by " +
                "FROM public.finance_reconciliation_source_item ";
    }

    private FinanceReconciliationRunItem mapRun(ResultSet rs) throws SQLException {
        return new FinanceReconciliationRunItem(
                uuid(rs, "reconciliation_run_id"),
                rs.getInt("company_id"),
                nullableInteger(rs, "branch_id"),
                rs.getString("reconciliation_type"),
                localDate(rs, "period_start"),
                localDate(rs, "period_end"),
                rs.getString("status"),
                rs.getBigDecimal("difference_amount"),
                nullableInteger(rs, "started_by"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by"));
    }

    private FinanceReconciliationItemItem mapItem(ResultSet rs) throws SQLException {
        return new FinanceReconciliationItemItem(
                uuid(rs, "reconciliation_item_id"),
                rs.getInt("company_id"),
                uuid(rs, "reconciliation_run_id"),
                uuid(rs, "reconciliation_source_item_id"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                uuid(rs, "ledger_line_id"),
                rs.getString("match_status"),
                rs.getBigDecimal("difference_amount"),
                rs.getString("resolution_status"),
                rs.getString("resolution_note"),
                rs.getString("resolution_proof_file_key"),
                rs.getString("resolution_proof_file_name"),
                rs.getString("resolution_proof_file_type"),
                nullableLong(rs, "resolution_proof_file_size"),
                instant(rs, "resolution_proof_uploaded_at"),
                nullableInteger(rs, "resolution_proof_uploaded_by"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by"));
    }

    private FinanceReconciliationSourceItem mapSourceItem(ResultSet rs) throws SQLException {
        return new FinanceReconciliationSourceItem(
                uuid(rs, "reconciliation_source_item_id"),
                rs.getInt("company_id"),
                nullableInteger(rs, "branch_id"),
                rs.getString("reconciliation_type"),
                rs.getString("source_system"),
                rs.getString("external_reference"),
                localDate(rs, "source_date"),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code"),
                rs.getString("description"),
                rs.getString("raw_payload_json"),
                rs.getString("status"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by"));
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
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

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
