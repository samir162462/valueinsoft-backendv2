package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateCompanyRequest;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class CompanyService {

    private final DbCompany dbCompany;
    private final DbUsers dbUsers;
    private final BranchService branchService;

    public CompanyService(DbCompany dbCompany, DbUsers dbUsers, BranchService branchService) {
        this.dbCompany = dbCompany;
        this.dbUsers = dbUsers;
        this.branchService = branchService;
    }

    public Company getCompanyForOwnerUserName(String userName) {
        User user = dbUsers.getUser(normalize(userName));
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        return dbCompany.getCompanyByOwnerId(user.getUserId());
    }

    public Company getCompanyAndBranchesByUserName(String userName) {
        Company company = dbCompany.getCompanyAndBranchesByUserName(normalize(userName));
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    public Company getCompanyById(int companyId) {
        Company company = dbCompany.getCompanyById(companyId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    public java.util.ArrayList<Company> getAllCompanies() {
        return dbCompany.getAllCompanies();
    }

    @Transactional
    public Company createCompany(CreateCompanyRequest request) {
        User owner = dbUsers.getUser(normalize(request.getOwnerName()));
        if (owner == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "OWNER_NOT_FOUND", "Owner user not found");
        }

        if (dbCompany.ownerHasCompany(owner.getUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_ALREADY_HAS_COMPANY", "The Owner already has Company!");
        }

        int companyId = dbCompany.createCompany(
                normalize(request.getCompanyName()),
                normalize(request.getPlan()),
                request.getEstablishPrice(),
                owner.getUserId(),
                normalizeNullable(request.getComImg()),
                normalize(request.getCurrency())
        );
        if (companyId <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANY_CREATE_FAILED", "Company could not be created");
        }

        if (!dbCompany.createCompanySchema(companyId)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "COMPANY_SCHEMA_CREATION_FAILED",
                    "Company schema provisioning failed"
            );
        }

        dbUsers.updateRole("public", owner.getUserId(), "Owner");

        String branchName = normalize(request.getBranchName());
        if (branchName.length() > 2) {
            branchService.createBranch(companyId, branchName, "Egypt");
        }

        Company company = dbCompany.getCompanyById(companyId);
        if (company == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "COMPANY_LOAD_FAILED", "Company was created but could not be loaded");
        }

        log.info("Created company {} for owner {}", companyId, owner.getUserName());
        return company;
    }

    @Transactional
    public void updateCompanyImage(int companyId, String imgFile) {
        int rows = dbCompany.updateCompanyImage(companyId, imgFile);
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        log.info("Updated image for company {}", companyId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
