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

    public BranchService(DbBranch dbBranch) {
        this.dbBranch = dbBranch;
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
        log.info("Created branch {} for company {}", branchId, companyId);
        return branchId;
    }

    public ArrayList<Branch> getBranchesByCompanyId(int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return new ArrayList<>(dbBranch.getBranchByCompanyId(companyId));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
