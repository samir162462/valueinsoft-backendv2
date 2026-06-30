package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountBalanceResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountLedgerItem;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceCreditRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceCreditResponse;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingBalanceServiceTest {

    private DbBillingWriteModels dbBillingWriteModels;
    private DbCompany dbCompany;
    private BillingAccountService billingAccountService;
    private FinanceOperationalPostingService financeOperationalPostingService;
    private BillingBalanceService service;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        dbCompany = Mockito.mock(DbCompany.class);
        billingAccountService = Mockito.mock(BillingAccountService.class);
        financeOperationalPostingService = Mockito.mock(FinanceOperationalPostingService.class);
        service = new BillingBalanceService(
                dbBillingWriteModels,
                dbCompany,
                billingAccountService,
                financeOperationalPostingService
        );

        when(dbCompany.getCompanyById(10)).thenReturn(new Company(
                10,
                "Acme",
                new Timestamp(System.currentTimeMillis()),
                "starter",
                500,
                "EGP",
                null,
                new ArrayList<>()
        ));
        when(billingAccountService.ensureBillingAccount(any())).thenReturn(101L);
    }

    @Test
    void creditBalanceCreatesApprovedCreditLedgerAndFinancePosting() {
        when(dbBillingWriteModels.lockBillingAccountBalance(10, "EGP")).thenReturn(balance("100.00"));
        when(dbBillingWriteModels.createBillingAccountLedgerEntry(
                anyLong(), anyInt(), anyString(), any(), anyString(), anyString(), any(), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(9001L);

        BillingBalanceCreditResponse response = service.creditBalance(10, creditRequest(), "admin");

        assertEquals(10, response.getCompanyId());
        assertEquals(101L, response.getBillingAccountId());
        assertEquals(9001L, response.getBillingAccountLedgerId());
        assertEquals(new BigDecimal("600.00"), response.getCreditedAmount());
        assertEquals(new BigDecimal("100.00"), response.getBalanceBefore());
        assertEquals(new BigDecimal("700.00"), response.getBalanceAfter());
        assertEquals("BANK_TRANSFER_TOP_UP", response.getFundingSource());
        assertEquals("CUSTOMER_PREPAYMENT", response.getCreditReason());

        verify(dbBillingWriteModels).updateBillingAccountAvailableBalance(101L, new BigDecimal("700.00"));
        verify(financeOperationalPostingService).enqueueBillingBalanceCredit(
                eq(10),
                eq(101L),
                eq(9001L),
                eq(new BigDecimal("600.00")),
                eq("EGP"),
                eq("BANK_TRANSFER_TOP_UP"),
                eq("CUSTOMER_PREPAYMENT"),
                eq("bank-transfer-123"),
                any(),
                eq("admin")
        );
    }

    @Test
    void duplicateCreditIdempotencyKeyReturnsExistingLedgerWithoutSecondMutation() {
        when(dbBillingWriteModels.findBillingAccountLedgerByIdempotencyKey(10, "credit-001"))
                .thenReturn(new BillingAccountLedgerItem(
                        9001L,
                        101L,
                        10,
                        "MANUAL_CREDIT",
                        new BigDecimal("600.00"),
                        "EGP",
                        "CREDIT",
                        new BigDecimal("100.00"),
                        new BigDecimal("700.00"),
                        "billing_balance_credit",
                        "bank-transfer-123",
                        "BANK_TRANSFER_TOP_UP",
                        "CUSTOMER_PREPAYMENT",
                        "APPROVED",
                        "Billing balance credit",
                        new Timestamp(System.currentTimeMillis())
                ));

        BillingBalanceCreditResponse response = service.creditBalance(10, creditRequest(), "admin");

        assertEquals(9001L, response.getBillingAccountLedgerId());
        assertEquals(new BigDecimal("700.00"), response.getBalanceAfter());
        verify(dbBillingWriteModels, never()).updateBillingAccountAvailableBalance(anyLong(), any());
        verify(dbBillingWriteModels, never()).createBillingAccountLedgerEntry(
                anyLong(), anyInt(), anyString(), any(), anyString(), anyString(), any(), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
        verify(financeOperationalPostingService, never()).enqueueBillingBalanceCredit(
                anyInt(), anyLong(), anyLong(), any(), anyString(), anyString(), anyString(), anyString(), any(), anyString()
        );
    }

    private BillingAccountBalanceResponse balance(String availableBalance) {
        return new BillingAccountBalanceResponse(
                10,
                101L,
                "EGP",
                new BigDecimal(availableBalance),
                "active",
                1L,
                new Timestamp(System.currentTimeMillis())
        );
    }

    private BillingBalanceCreditRequest creditRequest() {
        return new BillingBalanceCreditRequest(
                new BigDecimal("600.00"),
                "EGP",
                "BANK_TRANSFER_TOP_UP",
                "CUSTOMER_PREPAYMENT",
                "bank-transfer-123",
                "credit-001",
                "MANUAL_CREDIT",
                "Bank transfer top-up",
                "Paid by customer",
                "APPROVED"
        );
    }
}
