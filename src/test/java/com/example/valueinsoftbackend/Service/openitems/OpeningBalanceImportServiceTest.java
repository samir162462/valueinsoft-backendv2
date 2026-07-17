package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbApOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbOpenItemsReconciliation;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReconModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 6.4: the gating rule of the opening-balance import
 * (OPEN_ITEMS_BACKFILL_DECISION.md §3). PostgreSQL end-to-end coverage
 * (real inserts + audit trigger) belongs to the Docker suite.
 */
class OpeningBalanceImportServiceTest {

    private static final int COMPANY = 7;

    private DbOpenItemsReconciliation reconDb;
    private DbArOpenItem arDb;
    private DbApOpenItem apDb;
    private OpenItemsReconciliationService reconciliation;
    private OpeningBalanceImportService service;

    @BeforeEach
    void setUp() {
        reconDb = mock(DbOpenItemsReconciliation.class);
        arDb = mock(DbArOpenItem.class);
        apDb = mock(DbApOpenItem.class);
        reconciliation = mock(OpenItemsReconciliationService.class);
        service = new OpeningBalanceImportService(reconDb, arDb, apDb, reconciliation);
        when(arDb.getCompanyCurrency(COMPANY)).thenReturn("EGP");
        when(apDb.getCompanyCurrency(COMPANY)).thenReturn("EGP");
        when(reconDb.insertImportRun(eq(COMPANY), any())).thenReturn(42L);
    }

    private void arSide(String currentSubledger, String attributable) {
        when(reconciliation.side(COMPANY, "AR")).thenReturn(new OpenItemsReconModels.SideSnapshot(
                "AR", "1100", new BigDecimal(currentSubledger), null, null,
                new BigDecimal(attributable),
                new BigDecimal(currentSubledger).subtract(new BigDecimal(attributable))));
    }

    private OpenItemsReconModels.OpeningImportCommand command(boolean dryRun, BigDecimal approvedVariance,
                                                              String approver, String... amounts) {
        List<OpenItemsReconModels.OpeningBalanceLine> lines = java.util.Arrays.stream(amounts)
                .map(a -> new OpenItemsReconModels.OpeningBalanceLine(3, 11 + java.util.List.of(amounts).indexOf(a),
                        new BigDecimal(a)))
                .toList();
        return new OpenItemsReconModels.OpeningImportCommand("AR", lines, approvedVariance, approver, dryRun, null);
    }

    @Test
    void exactEqualityCommitsAndWritesAuditRow() {
        // control attributable 100; current subledger 0; importing 100 -> variance 0
        when(reconciliation.side(COMPANY, "AR"))
                .thenReturn(new OpenItemsReconModels.SideSnapshot(
                        "AR", "1100", BigDecimal.ZERO, null, null,
                        new BigDecimal("100"), new BigDecimal("-100")))
                .thenReturn(new OpenItemsReconModels.SideSnapshot(
                        "AR", "1100", new BigDecimal("100"), null, null,
                        new BigDecimal("100"), BigDecimal.ZERO));
        OpenItemsReconModels.OpeningImportResult result = service.importOpeningBalances(
                COMPANY, command(false, null, null, "100"), "sam");

        assertEquals("COMMITTED", result.status());
        assertEquals(1, result.partiesImported());
        verify(arDb).createOpenItem(eq(COMPANY), eq(3), anyInt(), eq("OPENING_BALANCE"), eq(null),
                anyString(), any(), any(), eq("EGP"), eq(new BigDecimal("100")), anyString(), eq("sam"));
        verify(reconDb).insertImportRun(eq(COMPANY), any());
    }

    @Test
    void unexplainedVarianceAbortsWithoutAnyItemInsert() {
        // importing 100 against attributable 130 -> variance -30, no approval
        arSide("0", "130");
        OpenItemsReconModels.OpeningImportResult result = service.importOpeningBalances(
                COMPANY, command(false, null, null, "100"), "sam");

        assertEquals("ABORTED", result.status());
        assertEquals(0, result.partiesImported());
        assertEquals(0, new BigDecimal("-30").compareTo(result.variance()));
        verify(arDb, never()).createOpenItem(anyInt(), anyInt(), anyInt(), anyString(), any(),
                anyString(), any(), any(), anyString(), any(), anyString(), anyString());
        // the abort decision itself is durably recorded
        verify(reconDb).insertImportRun(eq(COMPANY), any());
    }

    @Test
    void exactlyApprovedVarianceCommits() {
        when(reconciliation.side(COMPANY, "AR"))
                .thenReturn(new OpenItemsReconModels.SideSnapshot(
                        "AR", "1100", BigDecimal.ZERO, null, null,
                        new BigDecimal("130"), new BigDecimal("-130")))
                .thenReturn(new OpenItemsReconModels.SideSnapshot(
                        "AR", "1100", new BigDecimal("100"), null, null,
                        new BigDecimal("130"), new BigDecimal("-30")));
        OpenItemsReconModels.OpeningImportResult result = service.importOpeningBalances(
                COMPANY, command(false, new BigDecimal("-30"), "owner-sam", "100"), "sam");
        assertEquals("COMMITTED", result.status());
    }

    @Test
    void wrongApprovedVarianceAborts() {
        arSide("0", "130");
        OpenItemsReconModels.OpeningImportResult result = service.importOpeningBalances(
                COMPANY, command(false, new BigDecimal("-29"), "owner-sam", "100"), "sam");
        assertEquals("ABORTED", result.status());
    }

    @Test
    void approvedVarianceRequiresApprover() {
        arSide("0", "130");
        ApiException error = assertThrows(ApiException.class, () -> service.importOpeningBalances(
                COMPANY, command(false, new BigDecimal("-30"), " ", "100"), "sam"));
        assertEquals("OPENING_IMPORT_APPROVER_REQUIRED", error.getCode());
    }

    @Test
    void dryRunWritesNoItemsButRecordsProspectiveOutcome() {
        arSide("0", "100");
        OpenItemsReconModels.OpeningImportResult result = service.importOpeningBalances(
                COMPANY, command(true, null, null, "100"), "sam");

        assertEquals("DRY_RUN", result.status());
        assertEquals(0, new BigDecimal("100").compareTo(result.subledgerTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.variance()));
        verify(arDb, never()).createOpenItem(anyInt(), anyInt(), anyInt(), anyString(), any(),
                anyString(), any(), any(), anyString(), any(), anyString(), anyString());
        verify(reconDb).insertImportRun(eq(COMPANY), any());
    }

    @Test
    void rerunSkipsAlreadyImportedParties() {
        arSide("100", "100"); // party already imported previously; subledger already reconciled
        when(reconDb.openItemIdempotencyExists(COMPANY, "AR", "opening-ar-11")).thenReturn(true);

        OpenItemsReconModels.OpeningImportResult result = service.importOpeningBalances(
                COMPANY, command(false, null, null, "100"), "sam");

        assertEquals("COMMITTED", result.status());
        assertEquals(0, result.partiesImported());
        assertEquals(1, result.partiesSkipped());
        verify(arDb, never()).createOpenItem(anyInt(), anyInt(), anyInt(), anyString(), any(),
                anyString(), any(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void negativeOpeningBalancesAreRejected() {
        ApiException error = assertThrows(ApiException.class, () -> service.importOpeningBalances(
                COMPANY, command(false, null, null, "-5"), "sam"));
        assertEquals("OPENING_IMPORT_AMOUNT_INVALID", error.getCode());
    }

    @Test
    void concurrentDriftAfterInsertRollsBackWithConflict() {
        // Prospective check passes (0 + 100 vs 100), but the post-insert snapshot
        // shows a different world (someone posted mid-import).
        when(reconciliation.side(COMPANY, "AR"))
                .thenReturn(new OpenItemsReconModels.SideSnapshot("AR", "1100",
                        new BigDecimal("0"), null, null, new BigDecimal("100"), new BigDecimal("-100")))
                .thenReturn(new OpenItemsReconModels.SideSnapshot("AR", "1100",
                        new BigDecimal("100"), null, null, new BigDecimal("140"), new BigDecimal("-40")));

        ApiException error = assertThrows(ApiException.class, () -> service.importOpeningBalances(
                COMPANY, command(false, null, null, "100"), "sam"));
        assertEquals("OPENING_IMPORT_CONCURRENT_DRIFT", error.getCode());
    }
}
