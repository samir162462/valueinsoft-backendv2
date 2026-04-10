package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.CreateBranchRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@Slf4j
public class BranchService {

    private final DbBranch dbBranch;
    private final BusinessPackageCatalogService businessPackageCatalogService;

    public BranchService(DbBranch dbBranch, BusinessPackageCatalogService businessPackageCatalogService) {
        this.dbBranch = dbBranch;
        this.businessPackageCatalogService = businessPackageCatalogService;
    }

    @Transactional
    public int createBranch(CreateBranchRequest request) {
        return createBranch(request.getCompanyId(), request.getBranchName(), request.getBranchLocation());
    }

    @Transactional
    public int createBranch(int companyId, String branchName, String branchLocation) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        int branchId = dbBranch.createBranchWithTables(
                normalize(branchName),
                normalize(branchLocation),
                companyId
        );
        businessPackageCatalogService.provisionBranchCategoriesIfMissing(companyId, branchId);
        log.info("Created branch {} for company {}", branchId, companyId);
        return branchId;
    }

    public ArrayList<Branch> getBranchesByCompanyId(int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return new ArrayList<>(dbBranch.getBranchByCompanyId(companyId));
    }

    public Branch getBranchById(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return dbBranch.getBranchById(branchId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
