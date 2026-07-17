package com.example.valueinsoftbackend.Model.OpenItems;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * Stage 6 models: reconciliation snapshot, staging report, gated opening-balance import.
 * Design references: OPEN_ITEMS_RECONCILIATION_PLAN.md, OPEN_ITEMS_BACKFILL_DECISION.md.
 */
public final class OpenItemsReconModels {

    private OpenItemsReconModels() {
    }

    // ------------------------------------------------------------------
    // Reconciliation snapshot (6.1)
    // ------------------------------------------------------------------

    /** One control account's posted balance, split by attribution (review F9). */
    public record ControlBalance(String accountCode, BigDecimal totalBalance,
                                 BigDecimal billingPortion, BigDecimal attributable) {
    }

    /** One side (AR or AP) of the subledger-vs-control comparison. */
    public record SideSnapshot(String side, String accountCode, BigDecimal subledgerTotal,
                               BigDecimal controlTotal, BigDecimal billingExcluded,
                               BigDecimal controlAttributable, BigDecimal variance) {
    }

    public record ReconciliationSnapshot(int companyId, SideSnapshot ar, SideSnapshot ap) {
    }

    // ------------------------------------------------------------------
    // Staging report (6.2)
    // ------------------------------------------------------------------

    public record ClientOrderTypeTotal(int clientId, int branchId, String orderType,
                                       BigDecimal total, boolean hasBounceBacks) {
    }

    public record ClientReceiptTotals(int clientId, BigDecimal paidIn, BigDecimal paidOut) {
    }

    /**
     * Per-client staged AR balance with quality flags. Flagged rows must be reviewed and
     * manually approved; they never qualify for unedited import (backfill decision §2.3).
     */
    public record ClientStagedBalance(int clientId, BigDecimal creditOrderTotal, BigDecimal receiptsIn,
                                      BigDecimal receiptsOut, BigDecimal stagedBalance,
                                      List<String> flags) {
    }

    /** §3.4 proof row: purchase whose payment history is not explained by receipts. */
    public record ApDocumentProof(long stockLedgerId, int branchId, int supplierId,
                                  BigDecimal transTotal, BigDecimal remainingAmount,
                                  BigDecimal receiptsTotal, BigDecimal unexplainedDelta) {
    }

    /** §3.3 drift row: ledger remaining vs the mutable supplier running total. */
    public record SupplierDrift(int supplierId, int branchId, BigDecimal ledgerRemaining,
                                BigDecimal supplierRunningTotal) {
    }

    public record ArStagingReport(int companyId, List<ClientStagedBalance> clients,
                                  int flaggedCount, BigDecimal stagedTotal) {
    }

    public record ApStagingReport(int companyId, List<ApDocumentProof> unexplainedPurchases,
                                  List<SupplierDrift> drift) {
    }

    // ------------------------------------------------------------------
    // Gated opening-balance import (6.3)
    // ------------------------------------------------------------------

    public record OpeningBalanceLine(
            @Positive int branchId,
            @Positive int partyId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount) {
    }

    public record OpeningImportCommand(
            @NotNull String side,                    // AR | AP
            @NotNull List<OpeningBalanceLine> lines,
            BigDecimal approvedVariance,             // null => gate requires exact equality
            String approver,                         // required when approvedVariance != null
            boolean dryRun,
            String notes) {
    }

    public record OpeningImportResult(long runId, String side, boolean dryRun, String status,
                                      int partiesRequested, int partiesImported, int partiesSkipped,
                                      BigDecimal importedTotal, BigDecimal subledgerTotal,
                                      BigDecimal controlAttributable, BigDecimal variance,
                                      BigDecimal approvedVariance, List<Integer> skippedParties) {
    }

    /** Write model for the append-only audit row (V147). */
    public record ImportRunAudit(String side, boolean dryRun, String status, int partiesRequested,
                                 int partiesImported, int partiesSkipped, BigDecimal importedTotal,
                                 BigDecimal subledgerTotal, BigDecimal controlAttributable,
                                 BigDecimal variance, BigDecimal approvedVariance, String approver,
                                 String notes, String actor) {
    }

    public record ImportRunAuditRow(long runId, String side, boolean dryRun, String status,
                                    int partiesRequested, int partiesImported, int partiesSkipped,
                                    BigDecimal importedTotal, BigDecimal subledgerTotal,
                                    BigDecimal controlAttributable, BigDecimal variance,
                                    BigDecimal approvedVariance, String approver, String notes,
                                    String createdBy, Timestamp createdAt) {
    }
}
