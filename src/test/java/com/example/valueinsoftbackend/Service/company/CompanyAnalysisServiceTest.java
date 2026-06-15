package com.example.valueinsoftbackend.Service.company;

import com.example.valueinsoftbackend.DatabaseRequests.DbDVCompanyAnalysis;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyAnalysisServiceTest {
    private DbDVCompanyAnalysis dbDVCompanyAnalysis;
    private CompanyAnalysisService companyAnalysisService;

    @BeforeEach
    void setUp() {
        dbDVCompanyAnalysis = Mockito.mock(DbDVCompanyAnalysis.class);
        companyAnalysisService = new CompanyAnalysisService(dbDVCompanyAnalysis);
    }

    @Test
    void getCurrentMonthAnalysisSeedsTodayRecordForBranchAndQueriesMonth() {
        CompanyAnalysisRequest request = request(1074, 1095);
        CompanyAnalysis analysis = new CompanyAnalysis(2, 1000, 3, 0, 1, 0, 0, 0, Date.valueOf("2026-06-01"), 1);
        when(dbDVCompanyAnalysis.hasTodayRecord(eq(1074), eq(1095))).thenReturn(false);
        when(dbDVCompanyAnalysis.insertTodayRecord(eq(1074), eq(1095))).thenReturn(1);
        when(dbDVCompanyAnalysis.getCompanyAnalysis(eq(1074), eq(1095), eq("month"), Mockito.any(LocalDate.class)))
                .thenReturn(List.of(analysis));

        List<CompanyAnalysis> result = companyAnalysisService.getCurrentMonthAnalysis(request);

        assertEquals(1, result.size());
        assertEquals(1000, result.get(0).getIncome());
        verify(dbDVCompanyAnalysis).insertTodayRecord(1074, 1095);
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(dbDVCompanyAnalysis).getCompanyAnalysis(eq(1074), eq(1095), eq("month"), dateCaptor.capture());
        assertEquals(1, dateCaptor.getValue().getDayOfMonth());
    }

    @Test
    void getCurrentMonthAnalysisDoesNotSeedWhenAllBranchesRequested() {
        CompanyAnalysisRequest request = request(1074, 0);
        when(dbDVCompanyAnalysis.getCompanyAnalysis(eq(1074), eq(0), eq("month"), Mockito.any(LocalDate.class)))
                .thenReturn(List.of());

        companyAnalysisService.getCurrentMonthAnalysis(request);

        verify(dbDVCompanyAnalysis, never()).hasTodayRecord(Mockito.anyInt(), Mockito.anyInt());
        verify(dbDVCompanyAnalysis, never()).insertTodayRecord(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    void getCurrentMonthAnalysisRejectsInvalidCompanyId() {
        assertThrows(IllegalArgumentException.class, () -> companyAnalysisService.getCurrentMonthAnalysis(request(0, 1095)));
    }

    @Test
    void incrementCurrentDaySeedsMissingRecordThenIncrements() {
        CompanyAnalysisUpdateRequest request = updateRequest();
        when(dbDVCompanyAnalysis.hasTodayRecord(eq(1074), eq(1095))).thenReturn(false);
        when(dbDVCompanyAnalysis.insertTodayRecord(eq(1074), eq(1095))).thenReturn(1);
        when(dbDVCompanyAnalysis.incrementTodayRecord(eq(1074), eq(1095), eq(2), eq(1000), eq(3), eq(1), eq(4), eq(5), eq(6), eq(7)))
                .thenReturn(1);

        String result = companyAnalysisService.incrementCurrentDay(request);

        assertEquals("the user Role Updated!", result);
        verify(dbDVCompanyAnalysis).insertTodayRecord(1074, 1095);
        verify(dbDVCompanyAnalysis).incrementTodayRecord(1074, 1095, 2, 1000, 3, 1, 4, 5, 6, 7);
    }

    @Test
    void incrementCurrentDayDoesNotSeedWhenTodayRecordExists() {
        CompanyAnalysisUpdateRequest request = updateRequest();
        when(dbDVCompanyAnalysis.hasTodayRecord(eq(1074), eq(1095))).thenReturn(true);
        when(dbDVCompanyAnalysis.incrementTodayRecord(eq(1074), eq(1095), eq(2), eq(1000), eq(3), eq(1), eq(4), eq(5), eq(6), eq(7)))
                .thenReturn(1);

        companyAnalysisService.incrementCurrentDay(request);

        verify(dbDVCompanyAnalysis, never()).insertTodayRecord(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    void incrementCurrentDayRejectsFailedSeed() {
        CompanyAnalysisUpdateRequest request = updateRequest();
        when(dbDVCompanyAnalysis.hasTodayRecord(eq(1074), eq(1095))).thenReturn(false);
        when(dbDVCompanyAnalysis.insertTodayRecord(eq(1074), eq(1095))).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> companyAnalysisService.incrementCurrentDay(request));

        assertEquals("COMPANY_ANALYSIS_SEED_FAILED", exception.getCode());
    }

    @Test
    void incrementCurrentDayRejectsFailedIncrement() {
        CompanyAnalysisUpdateRequest request = updateRequest();
        when(dbDVCompanyAnalysis.hasTodayRecord(eq(1074), eq(1095))).thenReturn(true);
        when(dbDVCompanyAnalysis.incrementTodayRecord(eq(1074), eq(1095), eq(2), eq(1000), eq(3), eq(1), eq(4), eq(5), eq(6), eq(7)))
                .thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> companyAnalysisService.incrementCurrentDay(request));

        assertEquals("COMPANY_ANALYSIS_UPDATE_FAILED", exception.getCode());
    }

    private CompanyAnalysisRequest request(int companyId, int branchId) {
        CompanyAnalysisRequest request = new CompanyAnalysisRequest();
        request.setCompanyId(companyId);
        request.setBranchId(branchId);
        return request;
    }

    private CompanyAnalysisUpdateRequest updateRequest() {
        CompanyAnalysisUpdateRequest request = new CompanyAnalysisUpdateRequest();
        request.setCompanyId(1074);
        request.setBranchId(1095);
        request.setSales(2);
        request.setIncome(1000);
        request.setClientIn(3);
        request.setInvShortage(1);
        request.setDiscountByUser(4);
        request.setDamagedProducts(5);
        request.setReturnPurchases(6);
        request.setShiftEndsEarly(7);
        return request;
    }
}
