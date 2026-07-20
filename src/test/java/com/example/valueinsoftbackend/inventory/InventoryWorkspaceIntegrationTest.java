package com.example.valueinsoftbackend.inventory;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditPageResponse;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditRow;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditSummary;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogItem;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryExactMatchResult;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPagination;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventorySort;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventorySummaryResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryAudit.InventoryAuditSearchRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import com.example.valueinsoftbackend.Service.inventory.InventoryAuditService;
import com.example.valueinsoftbackend.Service.inventory.InventoryWorkspaceService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryWorkspaceIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_USER = "inventory_user:Owner";

    @MockitoBean
    private InventoryWorkspaceService inventoryWorkspaceService;

    @MockitoBean
    private InventoryAuditService inventoryAuditService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private TenantScopeGuard tenantScopeGuard;

    @BeforeEach
    void configureInventoryScope() {
        when(tenantScopeGuard.requireScope(eq(AUTHENTICATED_USER), any(), any()))
                .thenReturn(new TenantScopeGuard.ResolvedTenantScope(1074, 1095));
    }

    @Test
    void shouldRequireAuthenticationForInventoryWorkspace() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/quick-find")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quickFindPayload()))
                .andExpect(status().isUnauthorized());

        verify(inventoryWorkspaceService, never()).quickFind(any(), any(InventoryQuickFindRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldQuickFindInventoryItem() throws Exception {
        InventoryCatalogItem item = catalogItem();
        when(inventoryWorkspaceService.quickFind(eq(AUTHENTICATED_USER), any(InventoryQuickFindRequest.class)))
                .thenReturn(new InventoryQuickFindResponse(
                        "exact",
                        "IPH-001",
                        "barcode",
                        "barcode",
                        new InventoryExactMatchResult(true, "barcode", item),
                        new ArrayList<>(),
                        summary(),
                        null
                ));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/quick-find")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quickFindPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("exact"))
                .andExpect(jsonPath("$.exactMatch.found").value(true))
                .andExpect(jsonPath("$.exactMatch.product.productName").value("iPhone 15"))
                .andExpect(jsonPath("$.summary.resultCount").value(1));

        verify(inventoryWorkspaceService).quickFind(eq(AUTHENTICATED_USER), any(InventoryQuickFindRequest.class));
        verify(tenantScopeGuard).requireScope(AUTHENTICATED_USER, 1074, 1095);
        verify(authorizationService).assertAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.item.read"
        );
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRejectCrossTenantWorkspaceRequestBeforeServiceCall() throws Exception {
        doThrow(new ApiException(
                HttpStatus.FORBIDDEN,
                "TENANT_ACCESS_DENIED",
                "User does not belong to the requested tenant"
        )).when(tenantScopeGuard).requireScope(AUTHENTICATED_USER, 9999, 1095);

        String payload = """
                {
                  "companyId": 9999,
                  "branchId": 1095,
                  "query": "IPH-001",
                  "exactType": "auto",
                  "limit": 10
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/quick-find")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));

        verify(inventoryWorkspaceService, never()).quickFind(any(), any(InventoryQuickFindRequest.class));
        verify(authorizationService, never()).assertAuthenticatedCapability(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldBrowseInventoryCatalog() throws Exception {
        when(inventoryWorkspaceService.browseCatalog(eq(AUTHENTICATED_USER), any(InventoryCatalogBrowseRequest.class)))
                .thenReturn(catalogBrowseResponse());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/catalog/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(browsePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(41))
                .andExpect(jsonPath("$.data[0].stockStatus").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data[0].buyPrice").doesNotExist())
                .andExpect(jsonPath("$.pagination.totalRows").value(1))
                .andExpect(jsonPath("$.chipCounts.low_stock").value(1));

        verify(inventoryWorkspaceService).browseCatalog(eq(AUTHENTICATED_USER), any(InventoryCatalogBrowseRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldExposeInventoryCostOnlyWithCostCapability() throws Exception {
        when(authorizationService.hasAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.pricing.cost.read"
        )).thenReturn(true);
        when(inventoryWorkspaceService.browseCatalog(eq(AUTHENTICATED_USER), any(InventoryCatalogBrowseRequest.class)))
                .thenReturn(catalogBrowseResponse());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/catalog/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(browsePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].buyPrice").value(50000));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRejectCostFiltersWithoutCostCapability() throws Exception {
        String payload = """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "query": "phone",
                  "page": 1,
                  "pageSize": 25,
                  "filters": {
                    "buyPriceMin": 10000
                  }
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/catalog/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVENTORY_COST_ACCESS_DENIED"));

        verify(inventoryWorkspaceService, never()).browseCatalog(any(), any(InventoryCatalogBrowseRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateInventoryPreset() throws Exception {
        LinkedHashMap<String, Object> queryState = new LinkedHashMap<>();
        queryState.put("query", "low stock");
        when(inventoryWorkspaceService.createPreset(
                eq(AUTHENTICATED_USER),
                eq(1074),
                eq(1095),
                any(InventoryPresetCreateRequest.class)
        ))
                .thenReturn(new InventoryPresetResponse(
                        "preset-1",
                        "Low stock phones",
                        "branch",
                        "catalog",
                        1095,
                        "Owner",
                        AUTHENTICATED_USER,
                        true,
                        queryState
                ));

        String payload = """
                {
                  "name": "Low stock phones",
                  "scope": "branch",
                  "mode": "catalog",
                  "branchId": 1095,
                  "roleTarget": "Owner",
                  "queryState": {
                    "query": "low stock"
                  }
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presetId").value("preset-1"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.queryState.query").value("low stock"));

        verify(inventoryWorkspaceService).createPreset(
                eq(AUTHENTICATED_USER),
                eq(1074),
                eq(1095),
                any(InventoryPresetCreateRequest.class)
        );
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldSearchInventoryAudit() throws Exception {
        ArrayList<InventoryAuditRow> rows = new ArrayList<>();
        rows.add(new InventoryAuditRow(
                41L,
                null,
                "QUANTITY",
                null,
                "iPhone 15",
                "Phones",
                "Main Branch",
                5,
                3,
                2,
                6,
                50000,
                300000L,
                Timestamp.valueOf("2026-01-15 10:00:00")
        ));
        when(inventoryAuditService.search(eq(AUTHENTICATED_USER), any(InventoryAuditSearchRequest.class)))
                .thenReturn(new InventoryAuditPageResponse(
                        rows,
                        1,
                        25,
                        1,
                        1,
                        new InventoryAuditSummary(1, 5, 3, 2, 6, 300000, 1),
                        new ArrayList<>()
                ));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/audit/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(auditSearchPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.summary.totalClosingQty").value(6))
                .andExpect(jsonPath("$.totalItems").value(1));

        verify(inventoryAuditService).search(eq(AUTHENTICATED_USER), any(InventoryAuditSearchRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRejectInvalidInventoryWorkspaceRequestBeforeServiceCall() throws Exception {
        String payload = """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "query": ""
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/inventory/quick-find")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(inventoryWorkspaceService, never()).quickFind(any(), any(InventoryQuickFindRequest.class));
    }

    private String quickFindPayload() {
        return """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "query": "IPH-001",
                  "exactType": "auto",
                  "limit": 10
                }
                """;
    }

    private String browsePayload() {
        return """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "query": "phone",
                  "page": 1,
                  "pageSize": 25,
                  "sort": {
                    "field": "updatedAt",
                    "direction": "desc"
                  },
                  "chips": ["low_stock"]
                }
                """;
    }

    private String auditSearchPayload() {
        return """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "fromDate": "2026-01-01",
                  "toDate": "2026-01-31",
                  "query": "phone",
                  "page": 1,
                  "size": 25,
                  "sortField": "lastMovementDate",
                  "sortDirection": "desc"
                }
                """;
    }

    private InventoryCatalogItem catalogItem() {
        return new InventoryCatalogItem(
                41L,
                "iPhone 15",
                "IPH-001",
                null,
                "QUANTITY",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "electronics",
                "mobile-phone",
                88,
                "Apple Supplier",
                4,
                "LOW_STOCK",
                true,
                true,
                false,
                55000,
                50000,
                "2026-01-15T10:00:00Z",
                "2026-01-15T10:00:00Z"
        );
    }

    private InventoryCatalogBrowseResponse catalogBrowseResponse() {
        ArrayList<InventoryCatalogItem> items = new ArrayList<>();
        items.add(catalogItem());
        LinkedHashMap<String, Integer> chipCounts = new LinkedHashMap<>();
        chipCounts.put("low_stock", 1);
        return new InventoryCatalogBrowseResponse(
                "catalog",
                "phone",
                1,
                25,
                new InventorySort("updatedAt", "desc"),
                items,
                new InventoryPagination(1, 25, 1L, 1, false),
                summary(),
                chipCounts
        );
    }

    private InventorySummaryResponse summary() {
        return new InventorySummaryResponse(
                1L,
                1L,
                0L,
                1L,
                0L,
                1L,
                3L,
                3L,
                2L,
                0L,
                0L,
                1L,
                1L,
                1L
        );
    }
}
