package com.example.valueinsoftbackend.pos.offline.controller;

import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.pos.offline.config.OfflinePosWorkerProperties;
import com.example.valueinsoftbackend.pos.offline.service.AuditLogService;
import com.example.valueinsoftbackend.pos.offline.service.PosOfflineSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link PosOfflineAdminController} using MockMvc standalone setup.
 */
@ExtendWith(MockitoExtension.class)
class PosOfflineAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PosOfflineSyncService syncService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private OfflinePosWorkerProperties workerProperties;

    @InjectMocks
    private PosOfflineAdminController controller;

    private Principal principal;
    private static final String PRINCIPAL_NAME = "admin-user";
    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long BATCH_ID = 100L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        principal = mock(Principal.class);
        when(principal.getName()).thenReturn(PRINCIPAL_NAME);
    }

    @Test
    void recoverStuckSuccess() throws Exception {
        when(workerProperties.getStuckThresholdMinutes()).thenReturn(15);
        when(syncService.recoverStuckImports(eq(COMPANY_ID), eq(BRANCH_ID), eq(BATCH_ID), anyInt())).thenReturn(5);

        mockMvc.perform(post("/api/admin/pos/offline-sync/batches/{batchId}/recover-stuck", BATCH_ID)
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("RECOVER_STUCK"))
                .andExpect(jsonPath("$.processedCount").value(5));

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.offline.admin.process"));
    }

    @Test
    void processSuccess() throws Exception {
        when(syncService.processPendingImports(eq(COMPANY_ID), eq(BRANCH_ID), eq(BATCH_ID))).thenReturn(10);

        mockMvc.perform(post("/api/admin/pos/offline-sync/batches/{batchId}/process", BATCH_ID)
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("PROCESS"))
                .andExpect(jsonPath("$.processedCount").value(10));
    }

    @Test
    void validateSuccess() throws Exception {
        when(syncService.validateReadyImports(eq(COMPANY_ID), eq(BRANCH_ID), eq(BATCH_ID))).thenReturn(8);

        mockMvc.perform(post("/api/admin/pos/offline-sync/batches/{batchId}/validate", BATCH_ID)
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("VALIDATE"))
                .andExpect(jsonPath("$.processedCount").value(8));
    }

    @Test
    void postSuccess() throws Exception {
        when(syncService.postValidatedImports(eq(COMPANY_ID), eq(BRANCH_ID), eq(BATCH_ID))).thenReturn(7);

        mockMvc.perform(post("/api/admin/pos/offline-sync/batches/{batchId}/post", BATCH_ID)
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("POST"))
                .andExpect(jsonPath("$.processedCount").value(7));
    }

    @Test
    void recalculateSummarySuccess() throws Exception {
        mockMvc.perform(post("/api/admin/pos/offline-sync/batches/{batchId}/recalculate-summary", BATCH_ID)
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("RECALCULATE_SUMMARY"));

        verify(syncService).recalculateBatchSummary(COMPANY_ID, BRANCH_ID, BATCH_ID);
    }
}
