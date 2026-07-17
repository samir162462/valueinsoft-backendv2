package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbApOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbOpenItemsReconciliation;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReconModels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Stage 6.3: the GATED opening-balance import (OPEN_ITEMS_BACKFILL_DECISION.md §2/§3).
 *
 * <p>This is deliberately an application-managed job, NOT a Flyway migration: go-live is
 * per tenant and needs human sign-off between the staging report and the import, so it must
 * run per tenant at each tenant's go-live date rather than at deploy time for all tenants.</p>
 *
 * <p>Hard gating rule — enforced inside the SAME transaction as the inserts:
 * after importing, {@code subledger total == control-attributable balance} must hold, or the
 * difference must exactly equal the explicitly approved variance (with the approver recorded).
 * Any unexplained residue aborts and rolls back every inserted item. Tenants whose GL was
 * enabled after operational history began will ALWAYS route through the approved-variance
 * path — expected, not an error (reconciliation plan §4).</p>
 *
 * <p>Idempotency: deterministic keys {@code opening-ar-<clientId>} /
 * {@code opening-ap-<branchId>-<supplierId>}; parties already imported are skipped, so a
 * rerun is a no-op that still writes an audit row. Dry runs write NOTHING to the open-item
 * tables — they compute the prospective gate outcome and record it in the append-only audit
 * (V147) only.</p>
 */
@Service
@Slf4j
public class OpeningBalanceImportService {

    private final DbOpenItemsReconciliation reconDb;
    private final DbArOpenItem arDb;
    private final DbApOpenItem apDb;
    private final OpenItemsReconciliationService reconciliationService;

    public OpeningBalanceImportService(DbOpenItemsReconciliation reconDb, DbArOpenItem arDb,
                                       DbApOpenItem apDb,
                                       OpenItemsReconciliationService reconciliationService) {
        this.reconDb = reconDb;
        this.arDb = arDb;
        this.apDb = apDb;
        this.reconciliationService = reconciliationService;
    }

    @Transactional
    public OpenItemsReconModels.OpeningImportResult importOpeningBalances(int companyId,
                                                                          OpenItemsReconModels.OpeningImportCommand command,
                                                                          String actor) {
        String side = normalizeSide(command.side());
        validate(command, side);

        String currency = "AR".equals(side) ? arDb.getCompanyCurrency(companyId) : apDb.getCompanyCurrency(companyId);
        LocalDateTime now = LocalDateTime.now();

        // ------------------------------------------------------------------
        // Phase 1 — prospective gate check, NO item writes. If the gate fails,
        // this transaction commits ONLY the ABORTED/DRY_RUN audit row: nothing
        // to roll back, and the decision is still permanently recorded (the
        // "explicitly documented" requirement of the backfill decision §3).
        // ------------------------------------------------------------------
        ArrayList<Integer> skipped = new ArrayList<>();
        ArrayList<OpenItemsReconModels.OpeningBalanceLine> toImport = new ArrayList<>();
        HashSet<String> seenKeys = new HashSet<>();
        BigDecimal prospectiveTotal = BigDecimal.ZERO;

        for (OpenItemsReconModels.OpeningBalanceLine line : command.lines()) {
            String key = idempotencyKey(side, line);
            if (!seenKeys.add(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OPENING_IMPORT_DUPLICATE_PARTY",
                        "Party appears more than once in the import: " + key);
            }
            if (reconDb.openItemIdempotencyExists(companyId, side, key)) {
                skipped.add(line.partyId());
                continue;
            }
            toImport.add(line);
            prospectiveTotal = prospectiveTotal.add(line.amount());
        }

        OpenItemsReconModels.SideSnapshot before = reconciliationService.side(companyId, side);
        BigDecimal prospectiveSubledger = before.subledgerTotal().add(prospectiveTotal);
        BigDecimal prospectiveVariance = prospectiveSubledger.subtract(before.controlAttributable());
        boolean gatePasses = passes(prospectiveVariance, command.approvedVariance());

        if (command.dryRun() || !gatePasses) {
            String status = command.dryRun() ? "DRY_RUN" : "ABORTED";
            long runId = reconDb.insertImportRun(companyId, new OpenItemsReconModels.ImportRunAudit(
                    side, command.dryRun(), status, command.lines().size(), 0, skipped.size(),
                    prospectiveTotal, prospectiveSubledger, before.controlAttributable(),
                    prospectiveVariance, command.approvedVariance(), command.approver(),
                    command.notes(), actor));
            if (!command.dryRun()) {
                log.warn("Opening import ABORTED for company {} side {}: variance {} (approved: {})",
                        companyId, side, prospectiveVariance, command.approvedVariance());
            }
            return new OpenItemsReconModels.OpeningImportResult(runId, side, command.dryRun(), status,
                    command.lines().size(), 0, skipped.size(), prospectiveTotal, prospectiveSubledger,
                    before.controlAttributable(), prospectiveVariance, command.approvedVariance(), skipped);
        }

        // ------------------------------------------------------------------
        // Phase 2 — gate passed prospectively: insert items, then RE-verify with
        // post-insert truth in the same transaction. A failure here means the
        // subledger or GL moved concurrently mid-import; everything (items AND
        // audit) rolls back and the caller retries — correctness over audit for
        // this rare race, since no durable state changed.
        // ------------------------------------------------------------------
        BigDecimal importedTotal = BigDecimal.ZERO;
        int imported = 0;
        for (OpenItemsReconModels.OpeningBalanceLine line : toImport) {
            String key = idempotencyKey(side, line);
            if ("AR".equals(side)) {
                arDb.createOpenItem(companyId, line.branchId(), line.partyId(), "OPENING_BALANCE", null,
                        "OPENING-" + line.partyId(), now, now, currency, line.amount(), key, actor);
            } else {
                apDb.createOpenItem(companyId, line.branchId(), line.partyId(), "OPENING_BALANCE", null,
                        "OPENING-" + line.branchId() + "-" + line.partyId(), now, now, currency,
                        line.amount(), key, actor);
            }
            importedTotal = importedTotal.add(line.amount());
            imported++;
        }

        OpenItemsReconModels.SideSnapshot after = reconciliationService.side(companyId, side);
        BigDecimal variance = after.subledgerTotal().subtract(after.controlAttributable());
        if (!passes(variance, command.approvedVariance())) {
            throw new ApiException(HttpStatus.CONFLICT, "OPENING_IMPORT_CONCURRENT_DRIFT",
                    "Subledger or GL changed during the import (post-insert variance " + variance
                            + "); the import was rolled back — rerun it");
        }

        long runId = reconDb.insertImportRun(companyId, new OpenItemsReconModels.ImportRunAudit(
                side, false, "COMMITTED", command.lines().size(), imported, skipped.size(),
                importedTotal, after.subledgerTotal(), after.controlAttributable(), variance,
                command.approvedVariance(), command.approver(), command.notes(), actor));
        if (command.approvedVariance() != null && command.approvedVariance().signum() != 0) {
            log.warn("Opening variance document COMMITTED for company {} side {} by {}: "
                            + "{} parties, total {}, variance {}",
                    companyId, side, command.approver(), imported, importedTotal, variance);
        } else {
            log.info("Opening import COMMITTED for company {} side {}: {} parties, total {}, variance {}",
                    companyId, side, imported, importedTotal, variance);
        }

        return new OpenItemsReconModels.OpeningImportResult(runId, side, false, "COMMITTED",
                command.lines().size(), imported, skipped.size(), importedTotal,
                after.subledgerTotal(), after.controlAttributable(), variance,
                command.approvedVariance(), skipped);
    }

    private static boolean passes(BigDecimal variance, BigDecimal approvedVariance) {
        return variance.signum() == 0
                || (approvedVariance != null && variance.compareTo(approvedVariance) == 0);
    }

    private static String idempotencyKey(String side, OpenItemsReconModels.OpeningBalanceLine line) {
        return "AR".equals(side)
                ? "opening-ar-" + line.partyId()
                : "opening-ap-" + line.branchId() + "-" + line.partyId();
    }

    private static String normalizeSide(String side) {
        String normalized = side == null ? "" : side.trim().toUpperCase();
        if (!"AR".equals(normalized) && !"AP".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPENING_IMPORT_SIDE_INVALID",
                    "side must be AR or AP");
        }
        return normalized;
    }

    private static void validate(OpenItemsReconModels.OpeningImportCommand command, String side) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPENING_IMPORT_LINES_REQUIRED",
                    "At least one opening balance line is required");
        }
        for (OpenItemsReconModels.OpeningBalanceLine line : command.lines()) {
            if (line.amount() == null || line.amount().signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OPENING_IMPORT_AMOUNT_INVALID",
                        "Opening balances must be positive (advances are unapplied credit notes, "
                                + "never negative open items — party " + line.partyId() + ")");
            }
        }
        if (command.approvedVariance() != null
                && (command.approver() == null || command.approver().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPENING_IMPORT_APPROVER_REQUIRED",
                    "An approver is required when importing with an approved variance");
        }
    }
}
