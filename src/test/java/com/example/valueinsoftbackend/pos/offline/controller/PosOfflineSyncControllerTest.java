package com.example.valueinsoftbackend.pos.offline.controller;

import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.pos.offline.dto.request.DeviceHeartbeatRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineSyncUploadRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.RegisterPosDeviceRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.*;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.enums.PosClientType;
import com.example.valueinsoftbackend.pos.offline.enums.PosDeviceStatus;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.service.BootstrapDataService;
import com.example.valueinsoftbackend.pos.offline.service.PosDeviceService;
import com.example.valueinsoftbackend.pos.offline.service.PosOfflineSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderItemRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link PosOfflineSyncController} using MockMvc standalone setup.
 */
@ExtendWith(MockitoExtension.class)
class PosOfflineSyncControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private PosDeviceService deviceService;

    @Mock
    private BootstrapDataService bootstrapDataService;

    @Mock
    private PosOfflineSyncService syncService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private PosOfflineSyncController controller;

    private Principal principal;
    private static final String PRINCIPAL_NAME = "test-user";
    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        principal = mock(Principal.class);
        when(principal.getName()).thenReturn(PRINCIPAL_NAME);
    }

    @Test
    void registerDeviceSuccess() throws Exception {
        RegisterPosDeviceRequest request = new RegisterPosDeviceRequest(
                COMPANY_ID, BRANCH_ID, "DEV-001", "Counter 1", PosClientType.DESKTOP_POS, "Windows", "1.0.0");
        
        RegisterPosDeviceResponse response = new RegisterPosDeviceResponse(1001L, "DEV-001", PosDeviceStatus.ACTIVE, true, 24);
        when(deviceService.registerDevice(any(), eq(PRINCIPAL_NAME))).thenReturn(response);

        mockMvc.perform(post("/api/pos/device/register")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.device.register"));
    }

    @Test
    void heartbeatSuccess() throws Exception {
        DeviceHeartbeatRequest request = new DeviceHeartbeatRequest(COMPANY_ID, BRANCH_ID, "DEV-001", "1.0.1");
        
        when(deviceService.heartbeat(any(), eq(PRINCIPAL_NAME)))
                .thenReturn(new DeviceHeartbeatResponse(1001L, "DEV-001", PosDeviceStatus.ACTIVE, true, Instant.now()));

        mockMvc.perform(post("/api/pos/device/heartbeat")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.device.heartbeat"));
    }

    @Test
    void getBootstrapDataSuccess() throws Exception {
        when(bootstrapDataService.getBootstrapData(anyLong(), anyLong(), anyString(), any(), any(), anyInt(), eq(PRINCIPAL_NAME)))
                .thenReturn(new BootstrapDataResponse(COMPANY_ID, BRANCH_ID, "products", 1L, "sha256", Instant.now(), Instant.now(), List.of(), false, null));

        mockMvc.perform(get("/api/pos/bootstrap-data")
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString())
                        .param("dataType", "products"))
                .andExpect(status().isOk());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.bootstrap.read"));
    }

    @Test
    void uploadOfflineSyncSuccess() throws Exception {
        OfflineOrderRequest order = new OfflineOrderRequest(
                "ORD-001", "IDEM-001", Instant.now(), null, null, "POS", "USD",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                "PAID", "SHIFT-1", null, 
                List.of(new OfflineOrderItemRequest(1L, "BAR-001", "Product Snapshot", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN)), 
                List.of());

        OfflineSyncUploadRequest request = new OfflineSyncUploadRequest(
                COMPANY_ID, BRANCH_ID, 1001L, 501L, PosClientType.DESKTOP_POS, "Windows", "1.0.1", "BATCH-001", Instant.now(), Instant.now(), List.of(order));
        
        when(syncService.uploadOfflineSync(any(), eq(PRINCIPAL_NAME)))
                .thenReturn(new OfflineSyncUploadResponse(100L, "BATCH-001", PosSyncBatchStatus.PROCESSING, 1, 0, 0, 0, 0, List.of()));

        mockMvc.perform(post("/api/pos/offline-sync/upload")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.offline.sync"));
    }

    @Test
    void getSyncStatusSuccess() throws Exception {
        when(syncService.getSyncStatus(eq(COMPANY_ID), eq(BRANCH_ID), eq(100L), eq(PRINCIPAL_NAME)))
                .thenReturn(new SyncStatusResponse(100L, "BATCH-001", PosSyncBatchStatus.COMPLETED, 10, 10, 0, 0, 0, Instant.now(), Instant.now()));

        mockMvc.perform(get("/api/pos/offline-sync/status/100")
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.offline.status"));
    }

    @Test
    void getSyncErrorsSuccess() throws Exception {
        when(syncService.getSyncErrors(eq(COMPANY_ID), eq(BRANCH_ID), eq(100L), any(), anyInt(), eq(PRINCIPAL_NAME)))
                .thenReturn(new SyncErrorListResponse(COMPANY_ID, BRANCH_ID, 100L, List.of(), false, null));

        mockMvc.perform(get("/api/pos/offline-sync/errors/100")
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.offline.errors"));
    }

    @Test
    void retryOfflineOrderSuccess() throws Exception {
        when(syncService.retryOfflineOrder(eq(COMPANY_ID), eq(BRANCH_ID), eq(500L), eq(PRINCIPAL_NAME)))
                .thenReturn(new OfflineRetryResultResponse(500L, "ORD-001", "IDEM-001", OfflineOrderImportStatus.FAILED, OfflineOrderImportStatus.PROCESSING, 1, Instant.now(), true, "Retried"));

        mockMvc.perform(post("/api/pos/offline-sync/retry/500")
                        .principal(principal)
                        .param("companyId", COMPANY_ID.toString())
                        .param("branchId", BRANCH_ID.toString()))
                .andExpect(status().isOk());

        verify(authorizationService).assertAuthenticatedCapability(eq(PRINCIPAL_NAME), eq(1), eq(2), eq("pos.offline.retry"));
    }
}
