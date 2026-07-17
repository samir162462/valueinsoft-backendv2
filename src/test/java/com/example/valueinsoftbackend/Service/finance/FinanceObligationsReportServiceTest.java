package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceObligationsReport;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceObligationsReportModels;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceObligationsReportServiceTest {

    private DbFinanceObligationsReport repository;
    private AuthorizationService authorization;
    private FinanceObligationsReportService service;

    @BeforeEach
    void setUp() {
        repository = mock(DbFinanceObligationsReport.class);
        authorization = mock(AuthorizationService.class);
        service = new FinanceObligationsReportService(repository, authorization);
    }

    @Test
    void normalizesSideSearchAndCapsPageSize() {
        LocalDate asOf = LocalDate.of(2026, 7, 12);
        FinanceObligationsReportModels.Page expected = new FinanceObligationsReportModels.Page(
                7, 3, "RECEIVABLE", asOf, List.of(), List.of(), 200, 0, 0);
        when(repository.page(7, 3, "RECEIVABLE", asOf, "ali", 200, 0, false)).thenReturn(expected);

        assertEquals(expected, service.page("sam", 7, 3, " receivable ", asOf, " ali ", 500, 0));

        verify(authorization).assertAuthenticatedCapability("sam", 7, 3, "finance.report.read");
        verify(repository).page(7, 3, "RECEIVABLE", asOf, "ali", 200, 0, false);
    }

    @Test
    void rejectsUnsupportedSide() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.page("sam", 7, 3, "EMPLOYEE", LocalDate.now(), null, 50, 0));
        assertEquals("OBLIGATIONS_SIDE_INVALID", exception.getCode());
    }

    @Test
    void detailsRequireCurrency() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.details("sam", 7, 3, "PAYABLE", 11, "SUPPLIER", "", LocalDate.now()));
        assertEquals("OBLIGATIONS_CURRENCY_INVALID", exception.getCode());
    }

    @Test
    void detailsAcceptLegacyTwoLetterCurrencyCode() {
        LocalDate asOf = LocalDate.of(2026, 7, 12);
        FinanceObligationsReportModels.PartyDetails expected = new FinanceObligationsReportModels.PartyDetails(
                7, 3, "RECEIVABLE", asOf, null, List.of());
        when(repository.details(7, 3, "RECEIVABLE", 11, "CLIENT", "LE", asOf, false)).thenReturn(expected);

        assertEquals(expected, service.details("sam", 7, 3, "RECEIVABLE", 11, "CLIENT", "le", asOf));
        verify(repository).details(7, 3, "RECEIVABLE", 11, "CLIENT", "LE", asOf, false);
    }
}
