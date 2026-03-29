package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbDVCompanyAnalysis;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.CompanyAnalysis;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.CompanyAnalysisUpdateRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CompanyAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CompanyAnalysisService.class);

    private final DbDVCompanyAnalysis dbDVCompanyAnalysis;

    public CompanyAnalysisService(DbDVCompanyAnalysis dbDVCompanyAnalysis) {
        this.dbDVCompanyAnalysis = dbDVCompanyAnalysis;
    }

    @Transactional
    public List<CompanyAnalysis> getCurrentMonthAnalysis(CompanyAnalysisRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        if (request.getBranchId() > 0) {
            ensureTodayRecord(request.getCompanyId(), request.getBranchId());
        }
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        return dbDVCompanyAnalysis.getCompanyAnalysis(request.getCompanyId(), request.getBranchId(), "month", firstDayOfMonth);
    }

    @Transactional
    public String incrementCurrentDay(CompanyAnalysisUpdateRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        ensureTodayRecord(request.getCompanyId(), request.getBranchId());

        int rows = dbDVCompanyAnalysis.incrementTodayRecord(
                request.getCompanyId(),
                request.getBranchId(),
                request.getSales(),
                request.getIncome(),
                request.getClientIn(),
                request.getInvShortage(),
                request.getDiscountByUser(),
                request.getDamagedProducts(),
                request.getReturnPurchases(),
                request.getShiftEndsEarly()
        );

        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANY_ANALYSIS_UPDATE_FAILED", "Company analysis could not be updated");
        }

        log.debug("Incremented company analysis for company {} branch {}", request.getCompanyId(), request.getBranchId());
        return "the user Role Updated!";
    }

    private void ensureTodayRecord(int companyId, int branchId) {
        if (dbDVCompanyAnalysis.hasTodayRecord(companyId, branchId)) {
            return;
        }
        int rows = dbDVCompanyAnalysis.insertTodayRecord(companyId, branchId);
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANY_ANALYSIS_SEED_FAILED", "Company analysis record could not be initialized");
        }
        log.debug("Seeded company analysis for company {} branch {}", companyId, branchId);
    }
}
