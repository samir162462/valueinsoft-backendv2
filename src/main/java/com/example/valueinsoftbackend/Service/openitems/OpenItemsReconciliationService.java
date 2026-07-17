package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbOpenItemsReconciliation;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReconModels;
import com.example.valueinsoftbackend.Service.finance.PaymentTypeClassifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 6.1/6.2: subledger-vs-control reconciliation snapshot and the pre-import staging
 * report. Read-only; the gate that consumes the snapshot lives in
 * {@link OpeningBalanceImportService}.
 *
 * <p>The reconciliation target is EXPLAINED equality, never raw equality
 * (OPEN_ITEMS_RECONCILIATION_PLAN.md §2): tenant 1100 carries platform-billing postings
 * with no client (excluded via source-type filter), and pre-finance history has no GL
 * counterpart at all — such tenants route through the approved-variance path by design.</p>
 */
@Service
public class OpenItemsReconciliationService {

    public static final String AR_CONTROL_ACCOUNT = "1100";
    public static final String AP_CONTROL_ACCOUNT = "2100";

    private final DbOpenItemsReconciliation db;
    private final OpenItemsMetrics metrics;

    public OpenItemsReconciliationService(DbOpenItemsReconciliation db, OpenItemsMetrics metrics) {
        this.db = db;
        this.metrics = metrics;
    }

    public OpenItemsReconModels.ReconciliationSnapshot snapshot(int companyId) {
        return new OpenItemsReconModels.ReconciliationSnapshot(companyId,
                side(companyId, "AR"), side(companyId, "AP"));
    }

    public OpenItemsReconModels.SideSnapshot side(int companyId, String side) {
        boolean ar = "AR".equals(side);
        BigDecimal subledger = ar ? db.arSubledgerTotal(companyId) : db.apSubledgerTotal(companyId);
        OpenItemsReconModels.ControlBalance control = db.controlBalance(companyId,
                ar ? AR_CONTROL_ACCOUNT : AP_CONTROL_ACCOUNT, ar);
        OpenItemsReconModels.SideSnapshot result = new OpenItemsReconModels.SideSnapshot(side, control.accountCode(), subledger,
                control.totalBalance(), control.billingPortion(), control.attributable(),
                subledger.subtract(control.attributable()));
        metrics.updateReconciliationDrift(companyId, side, result.variance());
        return result;
    }

    /**
     * §3.2: per-client staged balances. Order pay types are aggregated in SQL per
     * (client, orderType) and classified HERE through the shared PaymentTypeClassifier —
     * the single normalization authority (roadmap rule 10) — never by SQL substring matching.
     */
    public OpenItemsReconModels.ArStagingReport arStagingReport(int companyId) {
        Map<Integer, BigDecimal> creditTotals = new HashMap<>();
        Map<Integer, Boolean> bounceFlags = new HashMap<>();
        for (Integer branchId : db.companyBranchIds(companyId)) {
            for (OpenItemsReconModels.ClientOrderTypeTotal row : db.clientOrderTotals(companyId, branchId)) {
                if (PaymentTypeClassifier.classify(row.orderType()).category()
                        != PaymentTypeClassifier.Category.RECEIVABLE) {
                    continue;
                }
                creditTotals.merge(row.clientId(), row.total(), BigDecimal::add);
                bounceFlags.merge(row.clientId(), row.hasBounceBacks(), Boolean::logicalOr);
            }
        }
        Map<Integer, OpenItemsReconModels.ClientReceiptTotals> receipts = new HashMap<>();
        for (OpenItemsReconModels.ClientReceiptTotals row : db.clientReceiptTotals(companyId)) {
            receipts.put(row.clientId(), row);
        }

        ArrayList<OpenItemsReconModels.ClientStagedBalance> clients = new ArrayList<>();
        BigDecimal stagedTotal = BigDecimal.ZERO;
        int flagged = 0;
        for (Map.Entry<Integer, BigDecimal> entry : creditTotals.entrySet()) {
            int clientId = entry.getKey();
            BigDecimal creditOrders = entry.getValue();
            OpenItemsReconModels.ClientReceiptTotals receiptTotals = receipts.get(clientId);
            BigDecimal paidIn = receiptTotals == null ? BigDecimal.ZERO : receiptTotals.paidIn();
            BigDecimal paidOut = receiptTotals == null ? BigDecimal.ZERO : receiptTotals.paidOut();
            BigDecimal staged = creditOrders.subtract(paidIn);

            ArrayList<String> flags = new ArrayList<>();
            if (Boolean.TRUE.equals(bounceFlags.get(clientId))) {
                flags.add("HAS_BOUNCE_BACKS");            // order totals were mutated (review F6)
            }
            if (paidOut.signum() < 0) {
                flags.add("HAS_NEGATIVE_RECEIPTS");        // payouts invisible to GL (review F8)
            }
            if (staged.signum() < 0) {
                flags.add("RECEIPTS_EXCEED_CREDIT_ORDERS"); // advance — import as unapplied credit, not open item
            }
            if (!flags.isEmpty()) {
                flagged++;
            }
            if (staged.signum() > 0) {
                stagedTotal = stagedTotal.add(staged);
            }
            clients.add(new OpenItemsReconModels.ClientStagedBalance(clientId, creditOrders,
                    paidIn, paidOut, staged, List.copyOf(flags)));
        }
        clients.sort(Comparator.comparing(OpenItemsReconModels.ClientStagedBalance::stagedBalance).reversed());
        return new OpenItemsReconModels.ArStagingReport(companyId, clients, flagged, stagedTotal);
    }

    /** §3.3/§3.4: AP proof and drift. Rows here are import-blocked pending review. */
    public OpenItemsReconModels.ApStagingReport apStagingReport(int companyId) {
        ArrayList<OpenItemsReconModels.SupplierDrift> drift = new ArrayList<>();
        for (Integer branchId : db.companyBranchIds(companyId)) {
            try {
                drift.addAll(db.supplierDrift(companyId, branchId));
            } catch (org.springframework.dao.DataAccessException missingTable) {
                // Branch without a supplier table (legacy tenants) — nothing to reconcile there.
            }
        }
        return new OpenItemsReconModels.ApStagingReport(companyId,
                db.apUnexplainedPurchases(companyId), drift);
    }

    public List<OpenItemsReconModels.ImportRunAuditRow> importRuns(int companyId, String side, int limit) {
        return db.importRuns(companyId, side, Math.min(Math.max(limit, 1), 200));
    }
}
