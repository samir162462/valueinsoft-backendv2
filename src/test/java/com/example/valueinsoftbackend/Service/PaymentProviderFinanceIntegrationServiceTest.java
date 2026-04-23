package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentProviderFinanceIntegrationServiceTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private DbBranch dbBranch;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private PaymentProviderFinanceIntegrationService service;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        dbBranch = Mockito.mock(DbBranch.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        service = new PaymentProviderFinanceIntegrationService(
                dbBillingWriteModels,
                dbBranch,
                financeOperationalPostingService);
    }

    @Test
    void enqueuePayMobSettlementBuildsCardSettlementForSuccessfulCallback() {
        when(dbBillingWriteModels.findBranchIdByExternalOrderId("paymob", "123456")).thenReturn(3);
        when(dbBranch.getBranchById(3)).thenReturn(new Branch(3, 7, "Main", "Cairo", null));

        service.enqueuePayMobSettlement("paymob", "123456", "987654", callback("card", "MasterCard", true, false, false, false));

        ArgumentCaptor<Map<String, Object>> extraPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(financeOperationalPostingService).enqueueImportedProviderSettlement(
                eq(7),
                eq(3),
                eq("card_settlement"),
                eq("provider-callback:paymob:987654"),
                eq(new BigDecimal("500.0000")),
                eq(new BigDecimal("0.0000")),
                eq(new BigDecimal("500.0000")),
                eq("card"),
                eq("bank"),
                eq("987654"),
                any(Timestamp.class),
                eq("system"),
                extraPayloadCaptor.capture());

        assertEquals("paymob", extraPayloadCaptor.getValue().get("providerCode"));
        assertEquals("123456", extraPayloadCaptor.getValue().get("externalOrderId"));
        assertEquals("987654", extraPayloadCaptor.getValue().get("providerEventId"));
    }

    @Test
    void enqueuePayMobSettlementBuildsWalletSettlementWhenSourceDataIsWallet() {
        when(dbBillingWriteModels.findBranchIdByExternalOrderId("paymob", "123456")).thenReturn(3);
        when(dbBranch.getBranchById(3)).thenReturn(new Branch(3, 7, "Main", "Cairo", null));

        service.enqueuePayMobSettlement("paymob", "123456", "987654", callback("wallet", "mobile_wallet", true, false, false, false));

        verify(financeOperationalPostingService).enqueueImportedProviderSettlement(
                eq(7),
                eq(3),
                eq("wallet_settlement"),
                eq("provider-callback:paymob:987654"),
                eq(new BigDecimal("500.0000")),
                eq(new BigDecimal("0.0000")),
                eq(new BigDecimal("500.0000")),
                eq("wallet"),
                eq("bank"),
                eq("987654"),
                any(Timestamp.class),
                eq("system"),
                any());
    }

    @Test
    void enqueuePayMobSettlementSkipsPendingOrRefundedCallbacks() {
        service.enqueuePayMobSettlement("paymob", "123456", "987654", callback("card", "MasterCard", true, true, false, false));
        service.enqueuePayMobSettlement("paymob", "123456", "987654", callback("card", "MasterCard", true, false, false, true));

        verify(financeOperationalPostingService, never()).enqueueImportedProviderSettlement(
                anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private PayMobTransactionCallbackRequest callback(String type,
                                                      String subType,
                                                      boolean success,
                                                      boolean pending,
                                                      boolean voided,
                                                      boolean refunded) {
        PayMobTransactionCallbackRequest request = new PayMobTransactionCallbackRequest();
        request.setType("TRANSACTION");

        PayMobTransactionCallbackRequest.OrderPayload order = new PayMobTransactionCallbackRequest.OrderPayload();
        order.setId(123456);

        PayMobTransactionCallbackRequest.SourceDataPayload sourceData = new PayMobTransactionCallbackRequest.SourceDataPayload();
        sourceData.setType(type);
        sourceData.setSubType(subType);
        sourceData.setPan("512345******2346");

        PayMobTransactionCallbackRequest.TransactionPayload transaction = new PayMobTransactionCallbackRequest.TransactionPayload();
        transaction.setId(987654);
        transaction.setPending(pending);
        transaction.setAmountCents(50000);
        transaction.setCreatedAt("2026-04-06T12:00:00.000000");
        transaction.setCurrency("EGP");
        transaction.setErrorOccured(false);
        transaction.setHasParentTransaction(false);
        transaction.setIntegrationId(1989683);
        transaction.setSecure3d(false);
        transaction.setSuccess(success);
        transaction.setAuth(false);
        transaction.setCapture(false);
        transaction.setStandalonePayment(true);
        transaction.setVoided(voided);
        transaction.setRefunded(refunded);
        transaction.setOwner(1234);
        transaction.setOrder(order);
        transaction.setSourceData(sourceData);

        request.setTransaction(transaction);
        return request;
    }
}
