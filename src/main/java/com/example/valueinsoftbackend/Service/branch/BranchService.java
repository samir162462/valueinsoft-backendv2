package com.example.valueinsoftbackend.Service.branch;

import com.example.valueinsoftbackend.Service.BusinessPackageCatalogService;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.CreateBranchRequest;
import com.example.valueinsoftbackend.Config.CacheConfig;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    @CacheEvict(cacheNames = {
            CacheConfig.BRANCHES_BY_COMPANY,
            CacheConfig.BRANCH_BY_ID,
            CacheConfig.COMPANY_BRANCHES_BY_USER,
            CacheConfig.CATEGORY_JSON_FLAT,
            CacheConfig.CATEGORY_PAIRS
    }, allEntries = true)
    public int createBranch(CreateBranchRequest request) {
        return createBranch(request.getCompanyId(), request.getBranchName(), request.getBranchLocation());
    }

    @Transactional
    @CacheEvict(cacheNames = {
            CacheConfig.BRANCHES_BY_COMPANY,
            CacheConfig.BRANCH_BY_ID,
            CacheConfig.COMPANY_BRANCHES_BY_USER,
            CacheConfig.CATEGORY_JSON_FLAT,
            CacheConfig.CATEGORY_PAIRS
    }, allEntries = true)
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

    @Cacheable(cacheNames = CacheConfig.BRANCHES_BY_COMPANY, key = "#companyId")
    public ArrayList<Branch> getBranchesByCompanyId(int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return new ArrayList<>(dbBranch.getBranchByCompanyId(companyId));
    }

    @Cacheable(cacheNames = CacheConfig.BRANCH_BY_ID, key = "#branchId")
    public Branch getBranchById(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return dbBranch.getBranchById(branchId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
