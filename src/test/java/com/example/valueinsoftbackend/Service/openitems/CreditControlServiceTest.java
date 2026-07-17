package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 5.3: the credit-control decision matrix. Concurrency serialization is covered by
 * the PostgreSQL suite (client-row FOR UPDATE); these tests pin the pure decision logic.
 */
class CreditControlServiceTest {

    private static final int COMPANY = 7;
    private static final int BRANCH = 3;
    private static final int CLIENT = 11;

    private DbArOpenItem repository;
    private DbBranchSettings settings;
    private CreditControlService service;

    @BeforeEach
    void setUp() {
        repository = mock(DbArOpenItem.class);
        settings = mock(DbBranchSettings.class);
        service = new CreditControlService(repository, settings);
        when(repository.getCompanyCurrency(COMPANY)).thenReturn("EGP");
    }

    private void mode(String value) {
        when(settings.getEffectiveValueMap(COMPANY, BRANCH))
                .thenReturn(Map.of("pos.creditControlMode", value));
    }

    private void client(String status, String limit) {
        when(repository.lockClientCredit(COMPANY, CLIENT)).thenReturn(
                new OpenItemsWriteModels.ClientCreditLock(CLIENT, new BigDecimal(limit), 30, status));
    }

    private void exposure(String openItems, String unappliedNotes) {
        when(repository.sumClientOpenExposure(COMPANY, CLIENT, "EGP")).thenReturn(new BigDecimal(openItems));
        when(repository.sumClientUnappliedCreditNotes(COMPANY, CLIENT, "EGP")).thenReturn(new BigDecimal(unappliedNotes));
    }

    @Test
    void offModeSkipsEntirelyWithoutLockingTheClient() {
        mode("OFF");
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, new BigDecimal("999999"));
        assertTrue(result.allowed());
        assertEquals("OFF", result.mode());
        verify(repository, never()).lockClientCredit(COMPANY, CLIENT);
    }

    @Test
    void unknownOrMissingSettingBehavesAsOff() {
        when(settings.getEffectiveValueMap(COMPANY, BRANCH)).thenReturn(Map.of());
        assertTrue(service.checkCreditSale(COMPANY, BRANCH, CLIENT, BigDecimal.TEN).allowed());
        mode("banana");
        assertTrue(service.checkCreditSale(COMPANY, BRANCH, CLIENT, BigDecimal.TEN).allowed());
        verify(repository, never()).lockClientCredit(COMPANY, CLIENT);
    }

    @Test
    void exactlyAtLimitPassesInBlockMode() {
        mode("BLOCK");
        client("NORMAL", "100");
        exposure("50", "0");
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, new BigDecimal("50"));
        assertTrue(result.allowed());
        assertFalse(result.warning());
        assertEquals(0, new BigDecimal("100").compareTo(result.newExposure()));
    }

    @Test
    void oneCentOverLimitIsDeniedInBlockMode() {
        mode("BLOCK");
        client("NORMAL", "100");
        exposure("50", "0");
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, new BigDecimal("50.01"));
        assertFalse(result.allowed());
        assertEquals("CREDIT_LIMIT_EXCEEDED", result.reasonCode());
    }

    @Test
    void overLimitIsAllowedWithWarningInWarnMode() {
        mode("WARN");
        client("NORMAL", "100");
        exposure("80", "0");
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, new BigDecimal("30"));
        assertTrue(result.allowed());
        assertTrue(result.warning());
        assertEquals("CREDIT_LIMIT_EXCEEDED", result.reasonCode());
    }

    @Test
    void unappliedCreditNotesReduceExposure() {
        mode("BLOCK");
        client("NORMAL", "100");
        exposure("100", "30");
        // effective exposure 70 + order 30 = 100 = limit -> allowed
        assertTrue(service.checkCreditSale(COMPANY, BRANCH, CLIENT, new BigDecimal("30")).allowed());
    }

    @Test
    void blockedStatusDeniesEvenInWarnMode() {
        mode("WARN");
        client("BLOCKED", "1000000");
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, BigDecimal.ONE);
        assertFalse(result.allowed());
        assertEquals("CREDIT_CLIENT_BLOCKED", result.reasonCode());
    }

    @Test
    void holdStatusDeniesNewCredit() {
        mode("BLOCK");
        client("HOLD", "1000000");
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, BigDecimal.ONE);
        assertFalse(result.allowed());
        assertEquals("CREDIT_CLIENT_ON_HOLD", result.reasonCode());
    }

    @Test
    void missingClientIsDenied() {
        mode("BLOCK");
        when(repository.lockClientCredit(COMPANY, CLIENT)).thenReturn(null);
        OpenItemsWriteModels.CreditCheckResult result =
                service.checkCreditSale(COMPANY, BRANCH, CLIENT, BigDecimal.ONE);
        assertFalse(result.allowed());
        assertEquals("CREDIT_CLIENT_NOT_FOUND", result.reasonCode());
    }
}
