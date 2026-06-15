package com.example.valueinsoftbackend.Service.branch;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.CreateBranchRequest;
import com.example.valueinsoftbackend.Service.BusinessPackageCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BranchServiceTest {
    private DbBranch dbBranch;
    private BusinessPackageCatalogService businessPackageCatalogService;
    private BranchService branchService;

    @BeforeEach
    void setUp() {
        dbBranch = Mockito.mock(DbBranch.class);
        businessPackageCatalogService = Mockito.mock(BusinessPackageCatalogService.class);
        branchService = new BranchService(dbBranch, businessPackageCatalogService);
    }

    @Test
    void createBranchTrimsInputCreatesBranchAndProvisionsCatalog() {
        when(dbBranch.createBranchWithTables(eq("Downtown"), eq("Cairo"), eq(1074))).thenReturn(1095);

        int branchId = branchService.createBranch(1074, "  Downtown  ", " Cairo ");

        assertEquals(1095, branchId);
        verify(dbBranch).createBranchWithTables("Downtown", "Cairo", 1074);
        verify(businessPackageCatalogService).provisionBranchCategoriesIfMissing(1074, 1095);
    }

    @Test
    void createBranchRequestDelegatesToCreateBranchFields() {
        CreateBranchRequest request = new CreateBranchRequest();
        request.setCompanyId(1074);
        request.setBranchName(" Main ");
        request.setBranchLocation(" Giza ");
        when(dbBranch.createBranchWithTables(eq("Main"), eq("Giza"), eq(1074))).thenReturn(2001);

        int branchId = branchService.createBranch(request);

        assertEquals(2001, branchId);
        verify(dbBranch).createBranchWithTables("Main", "Giza", 1074);
        verify(businessPackageCatalogService).provisionBranchCategoriesIfMissing(1074, 2001);
    }

    @Test
    void createBranchRejectsInvalidCompanyIdBeforeRepositoryCall() {
        assertThrows(IllegalArgumentException.class, () -> branchService.createBranch(0, "Main", "Giza"));

        verifyNoInteractions(dbBranch, businessPackageCatalogService);
    }

    @Test
    void getBranchesByCompanyIdReturnsRepositoryBranchesAsArrayList() {
        Branch branch = new Branch(1095, 1074, "Main", "Giza", Timestamp.valueOf("2026-06-01 10:00:00"));
        when(dbBranch.getBranchByCompanyId(eq(1074))).thenReturn(List.of(branch));

        var result = branchService.getBranchesByCompanyId(1074);

        assertEquals(1, result.size());
        assertEquals(1095, result.get(0).getBranchID());
        assertEquals("Main", result.get(0).getBranchName());
    }

    @Test
    void getBranchByIdDelegatesToRepository() {
        Branch branch = new Branch(1095, 1074, "Main", "Giza", Timestamp.valueOf("2026-06-01 10:00:00"));
        when(dbBranch.getBranchById(eq(1095))).thenReturn(branch);

        Branch result = branchService.getBranchById(1095);

        assertEquals(1095, result.getBranchID());
        assertEquals(1074, result.getBranchOfCompanyId());
    }
}
