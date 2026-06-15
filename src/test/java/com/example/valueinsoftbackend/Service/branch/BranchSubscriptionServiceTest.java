package com.example.valueinsoftbackend.Service.branch;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateSubscriptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchSubscriptionServiceTest {
    private DbBillingWriteModels dbBillingWriteModels;
    private BranchSubscriptionService branchSubscriptionService;

    @BeforeEach
    void setUp() {
        dbBillingWriteModels = Mockito.mock(DbBillingWriteModels.class);
        branchSubscriptionService = new BranchSubscriptionService(dbBillingWriteModels);
    }

    @Test
    void ensureLegacyMirroredSubscriptionReturnsExistingSubscriptionWhenPresent() {
        when(dbBillingWriteModels.findBranchSubscriptionIdByLegacySubscriptionId(eq(77))).thenReturn(9001L);

        long result = branchSubscriptionService.ensureLegacyMirroredSubscription(
                500L,
                company(),
                branch(),
                77,
                request()
        );

        assertEquals(9001L, result);
        verify(dbBillingWriteModels, never()).createBranchSubscription(
                Mockito.anyLong(),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyString()
        );
    }

    @Test
    void ensureLegacyMirroredSubscriptionCreatesPendingSubscriptionWhenMissing() {
        when(dbBillingWriteModels.findBranchSubscriptionIdByLegacySubscriptionId(eq(77))).thenReturn(null);
        when(dbBillingWriteModels.createBranchSubscription(
                eq(500L),
                eq(1095),
                eq(1074),
                eq(77),
                eq("pro"),
                eq("pending_payment"),
                eq(new BigDecimal("125.50")),
                eq(Date.valueOf("2026-06-01")),
                eq(Date.valueOf("2026-06-01")),
                eq(Date.valueOf("2026-07-01")),
                eq("{\"legacyStatus\":\"NP\"}")
        )).thenReturn(9010L);

        long result = branchSubscriptionService.ensureLegacyMirroredSubscription(
                500L,
                company(),
                branch(),
                77,
                request()
        );

        assertEquals(9010L, result);
    }

    @Test
    void markPaidByExternalOrderIdActivatesSubscriptionWithLegacyPaidMetadata() {
        branchSubscriptionService.markPaidByExternalOrderId("paymob", "ord-123");

        verify(dbBillingWriteModels).updateBranchSubscriptionStatusByExternalOrderId(
                "paymob",
                "ord-123",
                "active",
                "{\"legacyStatus\":\"PD\"}"
        );
    }

    @Test
    void markPaidByLegacySubscriptionIdActivatesSubscriptionWithLegacyPaidMetadata() {
        branchSubscriptionService.markPaidByLegacySubscriptionId(77);

        verify(dbBillingWriteModels).updateBranchSubscriptionStatusByLegacySubscriptionId(
                77,
                "active",
                "{\"legacyStatus\":\"PD\"}"
        );
    }

    private Company company() {
        return new Company(
                1074,
                "ValueINSoft",
                Timestamp.valueOf("2026-01-01 00:00:00"),
                "pro",
                0,
                "EGP",
                "",
                new ArrayList<>()
        );
    }

    private Branch branch() {
        return new Branch(1095, 1074, "Main", "Giza", Timestamp.valueOf("2026-01-01 00:00:00"));
    }

    private CreateSubscriptionRequest request() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setStartTime(LocalDate.of(2026, 6, 1));
        request.setEndTime(LocalDate.of(2026, 7, 1));
        request.setAmountToPay(new BigDecimal("125.50"));
        return request;
    }
}
