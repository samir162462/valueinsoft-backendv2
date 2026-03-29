package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Request.CreateBranchRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);

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

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
