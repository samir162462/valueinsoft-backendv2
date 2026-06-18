package com.example.valueinsoftbackend.company;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.BusinessPackageCatalogService;
import com.example.valueinsoftbackend.Service.branch.BranchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.Timestamp;
import java.util.ArrayList;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyCreationIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private DbCompany dbCompany;

    @MockBean
    private DbUsers dbUsers;

    @MockBean
    private BranchService branchService;

    @MockBean
    private BusinessPackageCatalogService businessPackageCatalogService;

    @Test
    void shouldCreateCompanyWithNewOwnerUserAndBranch() throws Exception {
        User owner = user(501, "owner_user", "owner@example.com");
        Company company = company(1074, "ValueINSoft");
        when(dbUsers.checkExistUsername(eq("owner_user"))).thenReturn(false);
        when(dbUsers.checkExistingEmail(eq("owner@example.com"))).thenReturn(false);
        when(dbUsers.getUser(eq("owner_user"))).thenReturn(owner);
        when(dbCompany.ownerHasCompany(eq(501))).thenReturn(false);
        when(dbCompany.createCompany(eq("ValueINSoft"), eq("pro"), eq(2500), eq(501), eq("logo.png"), eq("EGP")))
                .thenReturn(1074);
        when(dbCompany.createCompanySchema(eq(1074))).thenReturn(true);
        when(businessPackageCatalogService.resolveBusinessPackageId(eq("retail"))).thenReturn("retail");
        when(branchService.createBranch(eq(1074), eq("Main Branch"), eq("Egypt"))).thenReturn(1095);
        when(dbCompany.getCompanyById(eq(1074))).thenReturn(company);

        mockMvc.perform(MockMvcRequestBuilders.post("/Company/saveCompany")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newOwnerPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(1074))
                .andExpect(jsonPath("$.companyName").value("ValueINSoft"))
                .andExpect(jsonPath("$.currency").value("EGP"));

        verify(dbUsers).addUser(
                "owner_user",
                "securePassword",
                "owner@example.com",
                "Owner",
                "Owner",
                "User",
                1,
                "01000000000",
                0,
                null
        );
        verify(dbUsers).updateRole("public", 501, "Owner");
        verify(businessPackageCatalogService).bootstrapTenantForNewCompany(1074, "pro", "retail", "pro", 501);
        verify(dbUsers).updateUserBranch("owner_user", 1095);
    }

    @Test
    void shouldCreateCompanyForExistingOwnerAndLegacyBranchMajor() throws Exception {
        User owner = user(502, "existing_owner", "existing@example.com");
        when(dbUsers.getUserByUserNameOrEmail(eq("existing@example.com"))).thenReturn(owner);
        when(dbCompany.ownerHasCompany(eq(502))).thenReturn(false);
        when(dbCompany.createCompany(eq("Legacy Company"), eq("basic"), eq(0), eq(502), eq(null), eq("USD")))
                .thenReturn(2074);
        when(dbCompany.createCompanySchema(eq(2074))).thenReturn(true);
        when(businessPackageCatalogService.mapLegacyBranchMajorToBusinessPackage(eq("mobile"))).thenReturn("mobile-retail");
        when(dbCompany.getCompanyById(eq(2074))).thenReturn(company(2074, "Legacy Company"));

        mockMvc.perform(MockMvcRequestBuilders.post("/Company/saveCompany")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existingOwnerPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(2074))
                .andExpect(jsonPath("$.companyName").value("Legacy Company"));

        verify(dbUsers).updateRole("public", 502, "Owner");
        verify(businessPackageCatalogService).bootstrapTenantForNewCompany(2074, "basic", "mobile-retail", "basic", 502);
        verify(branchService, never()).createBranch(anyInt(), anyString(), anyString());
        verify(dbUsers, never()).updateUserBranch(anyString(), anyInt());
    }

    @Test
    void shouldRejectInvalidCreateCompanyPayloadBeforeServiceWork() throws Exception {
        String payload = """
                {
                  "companyName": "",
                  "branchName": "Main Branch",
                  "plan": "pro",
                  "ownerName": "Owner User",
                  "ownerEmail": "not-an-email",
                  "currency": "",
                  "ownerUser": {
                    "userName": "owner_user",
                    "password": "123",
                    "email": "bad-email",
                    "firstName": "Owner",
                    "lastName": "User",
                    "phone": "01000000000",
                    "gender": 1
                  }
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/Company/saveCompany")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasItem("companyName: companyName is required")))
                .andExpect(jsonPath("$.details", hasItem("currency: currency is required")))
                .andExpect(jsonPath("$.details", hasItem("ownerEmail: ownerEmail must be valid")))
                .andExpect(jsonPath("$.details", hasItem("ownerUser.email: ownerUser.email must be valid")))
                .andExpect(jsonPath("$.details", hasItem("ownerUser.password: ownerUser.password must be between 6 and 100 characters")));

        verify(dbCompany, never()).createCompany(anyString(), anyString(), anyInt(), anyInt(), any(), anyString());
    }

    @Test
    void shouldRejectDuplicateOwnerUsername() throws Exception {
        when(dbUsers.checkExistUsername(eq("owner_user"))).thenReturn(true);

        mockMvc.perform(MockMvcRequestBuilders.post("/Company/saveCompany")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newOwnerPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_USERNAME_EXISTS"))
                .andExpect(jsonPath("$.message").value("The owner username already exists"));

        verify(dbCompany, never()).createCompany(anyString(), anyString(), anyInt(), anyInt(), any(), anyString());
    }

    @Test
    void shouldRejectOwnerThatAlreadyHasCompany() throws Exception {
        User owner = user(503, "existing_owner", "existing@example.com");
        when(dbUsers.getUserByUserNameOrEmail(eq("existing@example.com"))).thenReturn(owner);
        when(dbCompany.ownerHasCompany(eq(503))).thenReturn(true);

        mockMvc.perform(MockMvcRequestBuilders.post("/Company/saveCompany")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existingOwnerPayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_ALREADY_HAS_COMPANY"))
                .andExpect(jsonPath("$.message").value("The Owner already has Company!"));

        verify(dbCompany, never()).createCompany(anyString(), anyString(), anyInt(), anyInt(), any(), anyString());
        verify(businessPackageCatalogService, never()).bootstrapTenantForNewCompany(anyInt(), anyString(), anyString(), anyString(), anyInt());
    }

    private String newOwnerPayload() {
        return """
                {
                  "companyName": " ValueINSoft ",
                  "branchName": " Main Branch ",
                  "plan": " pro ",
                  "EstablishPrice": 2500,
                  "ownerName": "Owner User",
                  "ownerEmail": "owner@example.com",
                  "currency": " EGP ",
                  "comImg": " logo.png ",
                  "businessPackage": "retail",
                  "ownerUser": {
                    "userName": " owner_user ",
                    "password": "securePassword",
                    "email": "owner@example.com",
                    "firstName": " Owner ",
                    "lastName": " User ",
                    "phone": " 01000000000 ",
                    "gender": 1
                  }
                }
                """;
    }

    private String existingOwnerPayload() {
        return """
                {
                  "companyName": " Legacy Company ",
                  "branchName": "No",
                  "plan": " basic ",
                  "establishPrice": 0,
                  "ownerName": "Existing Owner",
                  "ownerEmail": "existing@example.com",
                  "currency": " USD ",
                  "branchMajor": "mobile"
                }
                """;
    }

    private User user(int userId, String userName, String email) {
        return new User(
                userId,
                userName,
                "password",
                email,
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
