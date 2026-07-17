package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceDailyCashClosingReport;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingPaymentBreakdownRow;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingSummary;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FinanceDailyCashClosingReportServiceTest {

    @Test
    void sharedClassifierPreservesKnownPaymentBreakdownTotals() {
        FinanceDailyCashClosingReportService service = new FinanceDailyCashClosingReportService(
                mock(DbFinanceDailyCashClosingReport.class), mock(AuthorizationService.class), 5000);
        ArrayList<DailyCashClosingPaymentBreakdownRow> payments = new ArrayList<>();
        payments.add(row("Dirict", "10"));
        payments.add(row("CREDIT", "20"));
        payments.add(row("Visa", "30"));
        payments.add(row("Vodafone Cash", "40"));
        payments.add(row("مباشر", "50"));
        payments.add(row("Unknown", "60"));

        DailyCashClosingSummary summary = ReflectionTestUtils.invokeMethod(
                service,
                "buildSummary",
                new DbFinanceDailyCashClosingReport.SalesTotals(0, zero(), zero(), zero(), zero(), 0),
                new DbFinanceDailyCashClosingReport.ShiftTotals(zero(), zero(), zero(), zero(), 0),
                payments,
                new ArrayList<>(),
                zero());

        assertEquals(0, new BigDecimal("60.00").compareTo(summary.getCashSales()));
        assertEquals(0, new BigDecimal("30.00").compareTo(summary.getCardSales()));
        assertEquals(0, new BigDecimal("40.00").compareTo(summary.getWalletSales()));
        assertEquals(0, new BigDecimal("20.00").compareTo(summary.getCreditSales()));
    }

    private static DailyCashClosingPaymentBreakdownRow row(String method, String net) {
        BigDecimal amount = new BigDecimal(net);
        return new DailyCashClosingPaymentBreakdownRow(method, 1, amount, zero(), zero(), amount);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}
