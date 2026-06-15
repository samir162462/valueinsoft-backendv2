package com.example.valueinsoftbackend.Service.company;

import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.Request.CreateCompanyRequest;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.BusinessPackageCatalogService;
import com.example.valueinsoftbackend.Service.branch.BranchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyServiceTest {
    private DbCompany dbCompany;
    private DbUsers dbUsers;
    private BranchService branchService;
    private BusinessPackageCatalogService businessPackageCatalogService;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        dbCompany = Mockito.mock(DbCompany.class);
        dbUsers = Mockito.mock(DbUsers.class);
        branchService = Mockito.mock(BranchService.class);
        businessPackageCatalogService = Mockito.mock(BusinessPackageCatalogService.class);
        companyService = new CompanyService(dbCompany, dbUsers, branchService, businessPackageCatalogService);
    }

    @Test
    void getCompanyForOwnerUserNameTrimsUserNameAndLoadsCompanyByOwnerId() {
        User owner = user(501, "samir");
        Company company = company(1074, "ValueINSoft");
        when(dbUsers.getUser(eq("samir"))).thenReturn(owner);
        when(dbCompany.getCompanyByOwnerId(eq(501))).thenReturn(company);

        Company result = companyService.getCompanyForOwnerUserName("  samir  ");

        assertEquals(1074, result.getCompanyId());
        verify(dbUsers).getUser("samir");
        verify(dbCompany).getCompanyByOwnerId(501);
    }

    @Test
    void getCompanyForOwnerUserNameRejectsMissingUser() {
        when(dbUsers.getUser(eq("missing"))).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () ->
                companyService.getCompanyForOwnerUserName(" missing "));

        assertEquals("USER_NOT_FOUND", exception.getCode());
    }

    @Test
    void getCompanyAndBranchesByUserNameRejectsMissingCompany() {
        when(dbCompany.getCompanyAndBranchesByUserName(eq("cashier"))).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () ->
                companyService.getCompanyAndBranchesByUserName(" cashier "));

        assertEquals("COMPANY_NOT_FOUND", exception.getCode());
    }

    @Test
    void getCompanyByIdRejectsMissingCompany() {
        when(dbCompany.getCompanyById(eq(1074))).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> companyService.getCompanyById(1074));

        assertEquals("COMPANY_NOT_FOUND", exception.getCode());
    }

    @Test
    void createCompanyCreatesTenantBootstrapsCatalogCreatesBranchAndReturnsCompany() {
        CreateCompanyRequest request = createRequest();
        User owner = user(501, "owner");
        Company created = company(1074, "ValueINSoft");
        when(dbUsers.getUserByUserNameOrEmail(eq("owner"))).thenReturn(owner);
        when(dbCompany.ownerHasCompany(eq(501))).thenReturn(false);
        when(dbCompany.createCompany(eq("ValueINSoft"), eq("pro"), eq(2500), eq(501), eq("logo.png"), eq("EGP")))
                .thenReturn(1074);
        when(dbCompany.createCompanySchema(eq(1074))).thenReturn(true);
        when(businessPackageCatalogService.resolveBusinessPackageId(eq(" retail ")))
                .thenReturn("retail");
        when(dbCompany.getCompanyById(eq(1074))).thenReturn(created);

        Company result = companyService.createCompany(request);

        assertEquals(1074, result.getCompanyId());
        verify(dbUsers).updateRole("public", 501, "Owner");
        verify(businessPackageCatalogService).bootstrapTenantForNewCompany(1074, "pro", "retail", "pro", 501);
        verify(branchService).createBranch(1074, "Main", "Egypt");
    }

    @Test
    void createCompanyMapsLegacyBranchMajorWhenBusinessPackageMissing() {
        CreateCompanyRequest request = createRequest();
        request.setBusinessPackageId(" ");
        request.setBranchMajor("mobile");
        request.setBranchName("No");
        User owner = user(501, "owner");
        when(dbUsers.getUserByUserNameOrEmail(eq("owner"))).thenReturn(owner);
        when(dbCompany.ownerHasCompany(eq(501))).thenReturn(false);
        when(dbCompany.createCompany(eq("ValueINSoft"), eq("pro"), eq(2500), eq(501), eq("logo.png"), eq("EGP")))
                .thenReturn(1074);
        when(dbCompany.createCompanySchema(eq(1074))).thenReturn(true);
        when(businessPackageCatalogService.mapLegacyBranchMajorToBusinessPackage(eq("mobile"))).thenReturn("mobile-retail");
        when(dbCompany.getCompanyById(eq(1074))).thenReturn(company(1074, "ValueINSoft"));

        companyService.createCompany(request);

        verify(businessPackageCatalogService).bootstrapTenantForNewCompany(1074, "pro", "mobile-retail", "pro", 501);
        verify(branchService, never()).createBranch(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void createCompanyRejectsOwnerThatAlreadyHasCompany() {
        User owner = user(501, "owner");
        when(dbUsers.getUserByUserNameOrEmail(eq("owner"))).thenReturn(owner);
        when(dbCompany.ownerHasCompany(eq(501))).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> companyService.createCompany(createRequest()));

        assertEquals("OWNER_ALREADY_HAS_COMPANY", exception.getCode());
        verify(dbCompany, never()).createCompany(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.any(),
                Mockito.anyString()
        );
    }

    @Test
    void updateCompanyImageRejectsMissingCompany() {
        when(dbCompany.updateCompanyImage(eq(1074), eq("logo.png"))).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () ->
                companyService.updateCompanyImage(1074, "logo.png"));

        assertEquals("COMPANY_NOT_FOUND", exception.getCode());
    }

    @Test
    void updateCompanyImageAcceptsSingleUpdatedRow() {
        when(dbCompany.updateCompanyImage(eq(1074), eq("logo.png"))).thenReturn(1);

        companyService.updateCompanyImage(1074, "logo.png");

        verify(dbCompany).updateCompanyImage(1074, "logo.png");
    }

    private CreateCompanyRequest createRequest() {
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setCompanyName(" ValueINSoft ");
        request.setBranchName(" Main ");
        request.setPlan(" pro ");
        request.setEstablishPrice(2500);
        request.setOwnerName(" owner ");
        request.setComImg(" logo.png ");
        request.setCurrency(" EGP ");
        request.setBusinessPackageId(" retail ");
        return request;
    }

    private User user(int userId, String userName) {
        return new User(
                userId,
                userName,
                "password",
                userName + "@example.com",
                "First",
                "Last",
                "01000000000",
                "User",
                1,
                0,
                Timestamp.valueOf("2026-01-01 00:00:00")
        );
    }

    private Company company(int companyId, String companyName) {
        return new Company(
                companyId,
                companyName,
                Timestamp.valueOf("2026-01-01 00:00:00"),
                "pro",
                2500,
                "EGP",
                "logo.png",
                new ArrayList<>()
        );
    }
}
