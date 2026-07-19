package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryAudit.DbInventoryAuditReadModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditGroupSummary;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditPageResponse;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditRow;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditSummary;
import com.example.valueinsoftbackend.Model.Request.InventoryAudit.InventoryAuditSearchRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.ai.audit.AiUsageLogService;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAuditServiceCostAccessTest {

    private static final String USER = "manager@example.com";
    private static final String COST_CAPABILITY = "inventory.pricing.cost.read";

    @Mock
    private DbInventoryAuditReadModels readModels;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AiModelClient aiModelClient;
    @Mock
    private AiProperties aiProperties;
    @Mock
    private AiUsageLogService usageLogService;

    private InventoryAuditService service;

    @BeforeEach
    void setUp() {
        service = new InventoryAuditService(
                readModels,
                authorizationService,
                aiModelClient,
                aiProperties,
                new ObjectMapper(),
                5000,
                500,
                usageLogService
        );
    }

    @Test
    void searchRedactsEveryCostProjectionWithoutCostCapability() {
        InventoryAuditSearchRequest request = request();
        when(authorizationService.hasAuthenticatedCapability(USER, 7, 11, COST_CAPABILITY)).thenReturn(false);
        when(readModels.search(request)).thenReturn(response());

        InventoryAuditPageResponse result = service.search(USER, request);

        assertFalse(result.isCostVisible());
        assertNull(result.getRows().get(0).getUnitPrice());
        assertNull(result.getRows().get(0).getTotalValue());
        assertEquals(0L, result.getSummary().getTotalStockValue());
        assertEquals(0L, result.getGrouping().get(0).getTotalValue());
        verify(authorizationService).assertAuthenticatedCapability(USER, 7, 11, "inventory.item.read");
    }

    @Test
    void searchKeepsCostProjectionWithCostCapability() {
        InventoryAuditSearchRequest request = request();
        when(authorizationService.hasAuthenticatedCapability(USER, 7, 11, COST_CAPABILITY)).thenReturn(true);
        when(readModels.search(request)).thenReturn(response());

        InventoryAuditPageResponse result = service.search(USER, request);

        assertTrue(result.isCostVisible());
        assertEquals(125, result.getRows().get(0).getUnitPrice());
        assertEquals(625L, result.getRows().get(0).getTotalValue());
        assertEquals(625L, result.getSummary().getTotalStockValue());
    }

    @Test
    void excelExportRejectsBeforeReadingInventoryWhenCostCapabilityMissing() {
        InventoryAuditSearchRequest request = request();
        doAnswer(invocation -> {
            if (COST_CAPABILITY.equals(invocation.getArgument(3, String.class))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CAPABILITY_DENIED", "Missing cost capability");
            }
            return null;
        })
                .when(authorizationService)
                .assertAuthenticatedCapability(eq(USER), eq(7), eq(11), anyString());

        ApiException error = assertThrows(ApiException.class, () ->
                service.writeExcel(USER, request, new ByteArrayOutputStream()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(readModels, never()).fetchSummary(request);
    }

    private InventoryAuditSearchRequest request() {
        InventoryAuditSearchRequest request = new InventoryAuditSearchRequest();
        request.setCompanyId(7);
        request.setBranchId(11);
        request.setFromDate(LocalDate.of(2026, 7, 1));
        request.setToDate(LocalDate.of(2026, 7, 17));
        return request;
    }

    private InventoryAuditPageResponse response() {
        ArrayList<InventoryAuditRow> rows = new ArrayList<>();
        rows.add(new InventoryAuditRow(
                1L, null, "QUANTITY", null, "Widget", "General", "Main",
                0, 5, 0, 5, 125, 625L, null
        ));
        InventoryAuditSummary summary = new InventoryAuditSummary(1, 0, 5, 0, 5, 625, 0);
        ArrayList<InventoryAuditGroupSummary> grouping = new ArrayList<>();
        grouping.add(new InventoryAuditGroupSummary("General", 1, 5, 625));
        return new InventoryAuditPageResponse(rows, 1, 25, 1, 1, summary, grouping);
    }
}
