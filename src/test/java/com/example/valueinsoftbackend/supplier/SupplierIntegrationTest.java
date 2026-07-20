package com.example.valueinsoftbackend.supplier;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.SupplierArchiveRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierProductCreateRequest;
import com.example.valueinsoftbackend.Model.Request.SupplierUpdateRequest;
import com.example.valueinsoftbackend.Model.Response.SupplierAgingBucketResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAgingResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAuditEventResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAuditResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierOpenDocumentResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierReferenceResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierStatementLineResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierStatementResponse;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.Service.SupplierService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.security.TenantScopeGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupplierIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTHENTICATED_USER = "supplier_user:Owner";
    private static final int COMPANY_ID = 1074;
    private static final int BRANCH_ID = 1095;
    private static final int SUPPLIER_ID = 88;
    private static final int PRODUCT_ID = 41;
    private static final UUID POSTING_REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID JOURNAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @MockitoBean
    private SupplierService supplierService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
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
                .thenReturn(new TenantScopeGuard.ResolvedTenantScope(COMPANY_ID, BRANCH_ID));
    }

    @Test
    void shouldRequireAuthenticationForSupplierEndpoints() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/all/{companyId}/{branchId}", COMPANY_ID, BRANCH_ID))
                .andExpect(status().isUnauthorized());

        verify(supplierService, never()).getSuppliers(anyInt(), anyInt());
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldListAndGetSuppliers() throws Exception {
        when(supplierService.getSuppliers(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(activeSupplier()));
        when(supplierService.getSupplier(COMPANY_ID, BRANCH_ID, SUPPLIER_ID)).thenReturn(activeSupplier());

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/all/{companyId}/{branchId}", COMPANY_ID, BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supplierId").value(SUPPLIER_ID))
                .andExpect(jsonPath("$[0].supplierName").value("Main Supplier"))
                .andExpect(jsonPath("$[0].status").value("active"));

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/{supplierId}", COMPANY_ID, BRANCH_ID, SUPPLIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(SUPPLIER_ID))
                .andExpect(jsonPath("$.supplierRemaining").value(1200));

        verify(authorizationService, times(2)).assertAuthenticatedCapability(AUTHENTICATED_USER, COMPANY_ID, BRANCH_ID, "suppliers.account.read");
        verify(supplierService).getSuppliers(COMPANY_ID, BRANCH_ID);
        verify(supplierService).getSupplier(COMPANY_ID, BRANCH_ID, SUPPLIER_ID);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateSupplier() throws Exception {
        when(supplierService.createSupplier(any(SupplierCreateRequest.class))).thenReturn("the supplier added! ok 200");

        mockMvc.perform(MockMvcRequestBuilders.post("/suppliers/saveSupplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createSupplierPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value("the supplier added! ok 200"));

        verify(authorizationService).assertAuthenticatedCapability(AUTHENTICATED_USER, COMPANY_ID, BRANCH_ID, "suppliers.account.create");
        verify(supplierService).createSupplier(any(SupplierCreateRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldRejectInvalidCreateSupplierBeforeServiceCall() throws Exception {
        String payload = """
                {
                  "supplierName": "",
                  "supplierPhone1": "",
                  "branchId": 0,
                  "companyId": 0
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/suppliers/saveSupplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(supplierService, never()).createSupplier(any(SupplierCreateRequest.class));
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldUpdateAndDeleteSupplier() throws Exception {
        when(supplierService.updateSupplier(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), any(SupplierUpdateRequest.class)))
                .thenReturn("the supplier updates with (ok 200)");
        when(supplierService.deleteSupplier(COMPANY_ID, BRANCH_ID, SUPPLIER_ID)).thenReturn(true);

        String updatePayload = """
                {
                  "supplierName": "Updated Supplier",
                  "supplierPhone1": "01001112222",
                  "supplierLocation": "Giza",
                  "supplierMajor": "Electronics"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.put("/suppliers/{companyId}/{branchId}/update/{id}", COMPANY_ID, BRANCH_ID, SUPPLIER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Response").value("the supplier updates with (ok 200)"));

        mockMvc.perform(MockMvcRequestBuilders.delete("/suppliers/{companyId}/{branchId}/delete/{id}", COMPANY_ID, BRANCH_ID, SUPPLIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(authorizationService).assertAuthenticatedCapability(AUTHENTICATED_USER, COMPANY_ID, BRANCH_ID, "suppliers.account.edit");
        verify(authorizationService).assertAuthenticatedCapability(AUTHENTICATED_USER, COMPANY_ID, BRANCH_ID, "suppliers.account.delete");
        verify(supplierService).updateSupplier(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), any(SupplierUpdateRequest.class));
        verify(supplierService).deleteSupplier(COMPANY_ID, BRANCH_ID, SUPPLIER_ID);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldArchiveAndReactivateSupplier() throws Exception {
        when(supplierService.archiveSupplier(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), any(SupplierArchiveRequest.class)))
                .thenReturn(archivedSupplier());
        when(supplierService.reactivateSupplier(COMPANY_ID, BRANCH_ID, SUPPLIER_ID)).thenReturn(activeSupplier());

        mockMvc.perform(MockMvcRequestBuilders.post("/suppliers/{companyId}/{branchId}/{supplierId}/archive", COMPANY_ID, BRANCH_ID, SUPPLIER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "No longer active"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierStatus").value("archived"))
                .andExpect(jsonPath("$.archiveReason").value("No longer active"));

        mockMvc.perform(MockMvcRequestBuilders.post("/suppliers/{companyId}/{branchId}/{supplierId}/reactivate", COMPANY_ID, BRANCH_ID, SUPPLIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierStatus").value("active"));

        verify(supplierService).archiveSupplier(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), any(SupplierArchiveRequest.class));
        verify(supplierService).reactivateSupplier(COMPANY_ID, BRANCH_ID, SUPPLIER_ID);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldReturnSupplierReferencesStatementAgingAndAudit() throws Exception {
        when(supplierService.getSupplierReferences(COMPANY_ID, BRANCH_ID, SUPPLIER_ID)).thenReturn(references());
        when(supplierService.getSupplierStatement(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), eq(LocalDate.parse("2026-01-01")), eq(LocalDate.parse("2026-01-31"))))
                .thenReturn(statement());
        when(supplierService.getSupplierAging(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), eq(LocalDate.parse("2026-01-31"))))
                .thenReturn(aging());
        when(supplierService.getSupplierAudit(eq(COMPANY_ID), eq(BRANCH_ID), eq(SUPPLIER_ID), eq(LocalDate.parse("2026-01-01")), eq(LocalDate.parse("2026-01-31")), eq(0), eq(25)))
                .thenReturn(audit());

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/{supplierId}/references", COMPANY_ID, BRANCH_ID, SUPPLIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.references.inventoryTransactions").value(3))
                .andExpect(jsonPath("$.openBalance").value(1200));

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/{supplierId}/statement", COMPANY_ID, BRANCH_ID, SUPPLIER_ID)
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingBalance").value(1200))
                .andExpect(jsonPath("$.lines[0].sourceNumber").value("INV-1001"));

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/{supplierId}/ap-aging", COMPANY_ID, BRANCH_ID, SUPPLIER_ID)
                        .param("asOfDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.total").value(1200))
                .andExpect(jsonPath("$.openDocuments[0].bucket").value("1-30"));

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/{supplierId}/audit", COMPANY_ID, BRANCH_ID, SUPPLIER_ID)
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-01-31")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventType").value("SUPPLIER_UPDATED"))
                .andExpect(jsonPath("$.page").value(0));

        verify(supplierService).getSupplierReferences(COMPANY_ID, BRANCH_ID, SUPPLIER_ID);
        verify(supplierService).getSupplierStatement(COMPANY_ID, BRANCH_ID, SUPPLIER_ID, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        verify(supplierService).getSupplierAging(COMPANY_ID, BRANCH_ID, SUPPLIER_ID, LocalDate.parse("2026-01-31"));
        verify(supplierService).getSupplierAudit(COMPANY_ID, BRANCH_ID, SUPPLIER_ID, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), 0, 25);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldReturnSupplierSalesAndBoughtProducts() throws Exception {
        when(supplierService.getSupplierSales(COMPANY_ID, BRANCH_ID, SUPPLIER_ID)).thenReturn(List.of(inventoryTransaction()));
        when(supplierService.getSupplierBoughtProducts(COMPANY_ID, BRANCH_ID, SUPPLIER_ID)).thenReturn(List.of(supplierBProduct()));

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/SupplierSales/{supplierId}", COMPANY_ID, BRANCH_ID, SUPPLIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transId").value(7001))
                .andExpect(jsonPath("$[0].productName").value("iPhone 15"));

        mockMvc.perform(MockMvcRequestBuilders.get("/suppliers/{companyId}/{branchId}/SupplierBProduct/{supplierId}", COMPANY_ID, BRANCH_ID, SUPPLIER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sBPId").value(901))
                .andExpect(jsonPath("$[0].postingStatus").value("queued"));

        verify(supplierService).getSupplierSales(COMPANY_ID, BRANCH_ID, SUPPLIER_ID);
        verify(supplierService).getSupplierBoughtProducts(COMPANY_ID, BRANCH_ID, SUPPLIER_ID);
    }

    @Test
    @WithMockUser(username = AUTHENTICATED_USER, roles = {"USER"})
    void shouldCreateSupplierBoughtProduct() throws Exception {
        when(supplierService.createSupplierBoughtProduct(eq(COMPANY_ID), eq(BRANCH_ID), eq(PRODUCT_ID), any(SupplierProductCreateRequest.class)))
                .thenReturn(supplierBProduct());

        String payload = """
                {
                  "quantity": 2,
                  "cost": 50000,
                  "userName": "supplier_user",
                  "sPaid": 100000,
                  "time": "2026-01-15T10:00:00Z",
                  "desc": "Returned units",
                  "orderDetailsId": 0,
                  "adjustInventory": false
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/suppliers/{companyId}/{branchId}/saveSupplierBProduct/{productId}", COMPANY_ID, BRANCH_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sBPId").value(901))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.postingRequestId").value(POSTING_REQUEST_ID.toString()));

        verify(authorizationService).assertAuthenticatedCapability(AUTHENTICATED_USER, COMPANY_ID, BRANCH_ID, "suppliers.account.edit");
        verify(supplierService).createSupplierBoughtProduct(eq(COMPANY_ID), eq(BRANCH_ID), eq(PRODUCT_ID), any(SupplierProductCreateRequest.class));
    }

    private String createSupplierPayload() {
        return """
                {
                  "supplierName": "Main Supplier",
                  "supplierPhone1": "01001112222",
                  "supplierPhone2": "01001112223",
                  "supplierLocation": "Cairo",
                  "supplierMajor": "Electronics",
                  "branchId": 1095,
                  "companyId": 1074
                }
                """;
    }

    private Supplier activeSupplier() {
        return new Supplier(
                SUPPLIER_ID,
                "Main Supplier",
                "01001112222",
                "01001112223",
                "Cairo",
                "Electronics",
                5000,
                1200,
                "active",
                null,
                null,
                null
        );
    }

    private Supplier archivedSupplier() {
        return new Supplier(
                SUPPLIER_ID,
                "Main Supplier",
                "01001112222",
                "01001112223",
                "Cairo",
                "Electronics",
                5000,
                1200,
                "archived",
                "2026-01-15T10:00:00Z",
                501,
                "No longer active"
        );
    }

    private SupplierReferenceResponse references() {
        return new SupplierReferenceResponse(
                SUPPLIER_ID,
                false,
                true,
                Map.of("inventoryTransactions", 3L, "openReceipts", 1L),
                new BigDecimal("1200")
        );
    }

    private SupplierStatementResponse statement() {
        return new SupplierStatementResponse(
                SUPPLIER_ID,
                "Main Supplier",
                "EGP",
                BigDecimal.ZERO,
                new BigDecimal("5000"),
                new BigDecimal("3800"),
                new BigDecimal("1200"),
                List.of(new SupplierStatementLineResponse(
                        Instant.parse("2026-01-15T10:00:00Z"),
                        "SUPPLIER_RECEIPT",
                        "7001",
                        "INV-1001",
                        "Supplier receipt",
                        new BigDecimal("5000"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000"),
                        "posted",
                        POSTING_REQUEST_ID,
                        JOURNAL_ID,
                        null
                ))
        );
    }

    private SupplierAgingResponse aging() {
        return new SupplierAgingResponse(
                SUPPLIER_ID,
                "Main Supplier",
                "EGP",
                "2026-01-31",
                new SupplierAgingBucketResponse(
                        BigDecimal.ZERO,
                        new BigDecimal("1200"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("1200")
                ),
                List.of(new SupplierOpenDocumentResponse(
                        "SUPPLIER_RECEIPT",
                        "7001",
                        "INV-1001",
                        Instant.parse("2026-01-15T10:00:00Z"),
                        new BigDecimal("1200"),
                        16,
                        "1-30"
                ))
        );
    }

    private SupplierAuditResponse audit() {
        return new SupplierAuditResponse(
                SUPPLIER_ID,
                0,
                25,
                List.of(new SupplierAuditEventResponse(
                        Instant.parse("2026-01-15T10:00:00Z"),
                        "SUPPLIER_UPDATED",
                        AUTHENTICATED_USER,
                        "SUPPLIER",
                        String.valueOf(SUPPLIER_ID),
                        "Supplier updated",
                        "{\"supplierName\":\"Main Supplier\"}"
                ))
        );
    }

    private InventoryTransaction inventoryTransaction() {
        return new InventoryTransaction(
                7001,
                PRODUCT_ID,
                "iPhone 15",
                null,
                AUTHENTICATED_USER,
                SUPPLIER_ID,
                "Add",
                2,
                100000,
                "Cash",
                Timestamp.valueOf("2026-01-15 10:00:00"),
                0,
                2
        );
    }

    private SupplierBProduct supplierBProduct() {
        return new SupplierBProduct(
                901,
                PRODUCT_ID,
                SUPPLIER_ID,
                2,
                50000,
                AUTHENTICATED_USER,
                100000,
                Timestamp.valueOf("2026-01-15 10:00:00"),
                "Returned units",
                0,
                "queued",
                POSTING_REQUEST_ID,
                JOURNAL_ID,
                null
        );
    }
}
