package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvSales;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DVSalesYearly;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Request.CompanySalesWindowRequest;
import com.example.valueinsoftbackend.Model.Request.SalesOfMonthRequest;
import com.example.valueinsoftbackend.Model.Request.SalesOfYearRequest;
import com.example.valueinsoftbackend.Model.Request.SalesProductsByPeriodRequest;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import com.example.valueinsoftbackend.util.RequestDateParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SalesAnalyticsService {

    private final DbDvSales dbDvSales;
    private final DbDvCompany dbDvCompany;
    private final DbBranch dbBranch;

    public SalesAnalyticsService(DbDvSales dbDvSales, DbDvCompany dbDvCompany, DbBranch dbBranch) {
        this.dbDvSales = dbDvSales;
        this.dbDvCompany = dbDvCompany;
        this.dbBranch = dbBranch;
    }

    public List<DvSales> getMonthlySales(SalesOfMonthRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        RequestDateParser.parseSqlDate(request.getCurrentMonth(), "currentMonth");
        return dbDvSales.getMonthlySales(request.getCompanyId(), request.getCurrentMonth().trim(), request.getBranchId());
    }

    public List<DVSalesYearly> getYearlySales(SalesOfYearRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        return dbDvSales.getYearlySales(request.getCompanyId(), request.getYear(), request.getBranchId());
    }

    public List<SalesProduct> getSalesProductsByPeriod(SalesProductsByPeriodRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        RequestDateParser.parseSqlDate(request.getStartTime(), "startTime");
        RequestDateParser.parseSqlDate(request.getEndTime(), "endTime");
        return dbDvSales.getSalesProductsByPeriod(
                request.getCompanyId(),
                request.getBranchId(),
                request.getStartTime().trim(),
                request.getEndTime().trim()
        );
    }

    public List<Map<String, Object>> getCompanySalesWindow(CompanySalesWindowRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        ArrayList<Branch> branchArrayList = new ArrayList<>(dbBranch.getBranchByCompanyId(request.getCompanyId()));
        return dbDvCompany.getShiftTotalAndIncomeOfAllBranches(request.getCompanyId(), branchArrayList, request.getHours().trim());
    }

    public List<DvCompanyChartSalesIncome> getCompanySalesWindowPerDay(CompanySalesWindowRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        ArrayList<Branch> branchArrayList = new ArrayList<>(dbBranch.getBranchByCompanyId(request.getCompanyId()));
        return dbDvCompany.getShiftTotalAndIncomeOfAllBranchesPerDay(request.getCompanyId(), branchArrayList, request.getHours().trim());
    }
}
