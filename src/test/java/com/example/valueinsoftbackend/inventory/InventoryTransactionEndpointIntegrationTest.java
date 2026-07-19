package com.example.valueinsoftbackend.inventory;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.Model.Inventory.InventoryMovementType;
import com.example.valueinsoftbackend.Model.Inventory.InventoryStockMovement;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.CreateInventoryTransactionRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitStockInRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitTransferRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryTransactionQueryRequest;
import com.example.valueinsoftbackend.Model.ResponseModel.Inventory.SerializedUnitScanResponse;
import com.example.valueinsoftbackend.Service.SerializedInventoryService;
import com.example.valueinsoftbackend.Service.inventory.InventoryTransactionService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryTransactionEndpointIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_USER = "inventory_user:Owner";

    @MockBean
    private InventoryTransactionService inventoryTransactionService;

    @MockBean
    private SerializedInventoryService serializedInventoryService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private TenantScopeGuard tenantScopeGuard;

    @BeforeEach
    void setUp() {
        doNothing().when(authorizationService).assertAuthenticatedCapability(
                anyString(),
                anyInt(),
                nullable(Integer.class),
                anyString()
        );
        when(tenantScopeGuard.requireScope(anyString(), nullable(Integer.class), nullable(Integer.class)))
                .thenReturn(new TenantScopeGuard.ResolvedTenantScope(1074, 1095));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateInventoryTransactionThroughEndpoint() throws Exception {
        String payload = """
                {
                  "productId": 41,
                  "userName": "inventory_user",
                  "supplierId": 88,
                  "transactionType": "Add",
                  "numItems": 10,
                  "transTotal": 5000,
                  "payType": "Cash",
                  "time": "2026-01-15T10:00:00Z",
                  "remainingAmount": 0,
                  "branchId": 1095,
                  "companyId": 1074
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/invTrans/AddTransaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchId").value(1095));

        verify(authorizationService).assertAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.adjustment.create"
        );
        verify(inventoryTransactionService).addTransaction(any(CreateInventoryTransactionRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldQueryInventoryTransactionsThroughEndpoint() throws Exception {
        when(inventoryTransactionService.getTransactions(any(InventoryTransactionQueryRequest.class)))
                .thenReturn(List.of(new InventoryTransaction(
                        7001,
                        41,
                        "iPhone 15",
                        null,
                        "inventory_user",
                        88,
                        "Add",
                        10,
                        5000,
                        "Cash",
                        Timestamp.valueOf("2026-01-15 10:00:00"),
                        0,
                        10,
                        "electronics",
                        "mobile-phone"
                )));

        String payload = """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "startTime": "2026-01-01",
                  "endTime": "2026-01-31"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/invTrans/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$[0].transId").value(7001))
                .andExpect(jsonPath("$[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$[0].runningBalance").value(10));

        verify(authorizationService).assertAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.item.read"
        );
        verify(inventoryTransactionService).getTransactions(any(InventoryTransactionQueryRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldAddSerializedStockInThroughEndpoint() throws Exception {
        when(inventoryTransactionService.addSerializedStockIn(any(SerializedUnitStockInRequest.class)))
                .thenReturn(List.of(9001L, 9002L));

        mockMvc.perform(MockMvcRequestBuilders.post("/invTrans/AddSerializedStockIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serializedStockInPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0]").value(9001))
                .andExpect(jsonPath("$[1]").value(9002));

        verify(authorizationService).assertAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.adjustment.create"
        );
        verify(inventoryTransactionService).addSerializedStockIn(any(SerializedUnitStockInRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldTransferSerializedUnitsThroughEndpoint() throws Exception {
        String payload = """
                {
                  "companyId": 1074,
                  "fromBranchId": 1095,
                  "toBranchId": 1096,
                  "productId": 41,
                  "productUnitIds": [9001, 9002],
                  "actorName": "inventory_user",
                  "idempotencyKey": "transfer-001"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/invTrans/TransferSerializedUnits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.title").value("serialized units transferred"));

        verify(authorizationService).assertAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.adjustment.create"
        );
        verify(serializedInventoryService).transferSerializedUnits(any(SerializedUnitTransferRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldScanSerializedUnitThroughEndpoint() throws Exception {
        when(serializedInventoryService.scanUnit(eq(1074L), eq(1095L), eq("IMEI-001")))
                .thenReturn(new SerializedUnitScanResponse(
                        true,
                        true,
                        "OK",
                        "SERIALIZED_UNIT_AVAILABLE",
                        "Unit is available",
                        9001L,
                        1074L,
                        1095L,
                        41L,
                        "iPhone 15",
                        TrackingType.IMEI,
                        "IMEI-001",
                        "IMEI-001",
                        null,
                        ProductUnitStatus.AVAILABLE,
                        "NEW"
                ));

        mockMvc.perform(MockMvcRequestBuilders.get("/invTrans/SerializedScan/{companyId}/{branchId}/{scanCode}", 1074, 1095, "IMEI-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.productUnitId").value(9001));

        verify(authorizationService).assertAuthenticatedCapability(
                AUTHENTICATED_USER,
                1074,
                1095,
                "inventory.item.read"
        );
        verify(serializedInventoryService).scanUnit(1074L, 1095L, "IMEI-001");
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldListSerializedUnitsAndMovementHistoryThroughEndpoints() throws Exception {
        when(serializedInventoryService.listProductUnits(eq(1074L), eq(1095L), eq(41L), eq(ProductUnitStatus.AVAILABLE)))
                .thenReturn(List.of(productUnit()));
        when(serializedInventoryService.countAvailableSerializedUnits(eq(1074L), eq(1095L), eq(41L)))
                .thenReturn(1L);
        when(serializedInventoryService.listProductMovementHistory(eq(1074L), eq(1095L), eq(41L), eq(5)))
                .thenReturn(List.of(stockMovement()));

        mockMvc.perform(MockMvcRequestBuilders.get("/invTrans/SerializedUnits/{companyId}/{branchId}/{productId}", 1074, 1095, 41)
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productUnitId").value(9001))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        mockMvc.perform(MockMvcRequestBuilders.get("/invTrans/SerializedAvailability/{companyId}/{branchId}/{productId}", 1074, 1095, 41))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));

        mockMvc.perform(MockMvcRequestBuilders.get("/invTrans/StockMovements/{companyId}/{branchId}/{productId}", 1074, 1095, 41)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementType").value("STOCK_IN"))
                .andExpect(jsonPath("$[0].quantityDelta").value(1));

        verify(serializedInventoryService).listProductUnits(1074L, 1095L, 41L, ProductUnitStatus.AVAILABLE);
        verify(serializedInventoryService).countAvailableSerializedUnits(1074L, 1095L, 41L);
        verify(serializedInventoryService).listProductMovementHistory(1074L, 1095L, 41L, 5);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRejectInvalidInventoryTransactionBeforeServiceCall() throws Exception {
        String payload = """
                {
                  "productId": 0,
                  "userName": "",
                  "supplierId": 0,
                  "transactionType": "",
                  "numItems": 10,
                  "transTotal": 5000,
                  "payType": "",
                  "time": "",
                  "remainingAmount": -1,
                  "branchId": 0,
                  "companyId": 0
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/invTrans/AddTransaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(inventoryTransactionService, never()).addTransaction(any(CreateInventoryTransactionRequest.class));
    }

    private String serializedStockInPayload() {
        return """
                {
                  "companyId": 1074,
                  "branchId": 1095,
                  "productId": 41,
                  "trackingType": "IMEI",
                  "supplierId": 88,
                  "purchaseReferenceType": "SUPPLIER_RECEIPT",
                  "purchaseReferenceId": "SR-1001",
                  "actorName": "inventory_user",
                  "idempotencyKey": "stock-in-001",
                  "units": [
                    {
                      "unitIdentifier": "490154203237518",
                      "imei": "490154203237518",
                      "conditionCode": "NEW"
                    },
                    {
                      "unitIdentifier": "356938035643809",
                      "imei": "356938035643809",
                      "conditionCode": "NEW"
                    }
                  ]
                }
                """;
    }

    private ProductUnit productUnit() {
        Timestamp now = Timestamp.valueOf("2026-01-15 10:00:00");
        return new ProductUnit(
                9001L,
                1074L,
                1095L,
                41L,
                TrackingType.IMEI,
                "490154203237518",
                "490154203237518",
                null,
                ProductUnitStatus.AVAILABLE,
                "NEW",
                null,
                new BigDecimal("70000.0000"),
                88L,
                "SUPPLIER",
                null,
                "SUPPLIER_RECEIPT",
                "SR-1001",
                null,
                null,
                null,
                null,
                null,
                now,
                null,
                null,
                now,
                now,
                now,
                1
        );
    }

    private InventoryStockMovement stockMovement() {
        return new InventoryStockMovement(
                8001L,
                1074L,
                1095L,
                41L,
                9001L,
                InventoryMovementType.STOCK_IN,
                BigDecimal.ONE,
                null,
                null,
                "SUPPLIER_RECEIPT",
                "SR-1001",
                null,
                88L,
                null,
                null,
                "inventory_user",
                null,
                "stock-in-001",
                Timestamp.valueOf("2026-01-15 10:00:00")
        );
    }
}
