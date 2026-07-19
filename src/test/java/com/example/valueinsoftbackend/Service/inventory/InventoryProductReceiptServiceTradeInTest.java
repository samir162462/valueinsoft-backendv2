package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductUnitRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitInput;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.AcquisitionSource;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptDetailsRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptOperationMode;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptRequest;
import com.example.valueinsoftbackend.Model.Response.InventoryReceipt.ProductReceiptResponse;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Receipt flow: supplier remains the untouched default; the client trade-in
 * branch validates the client, stamps party + condition, writes the payable
 * subledger row, and never touches supplier totals or the legacy supplier
 * transaction.
 */
class InventoryProductReceiptServiceTradeInTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 10000;

    private DbInventoryProductReceiptRepository receiptRepository;
    private DbPosProductCommandRepository productCommandRepository;
    private DbInventoryProductUnitRepository productUnitRepository;
    private DbInventoryStockMovementRepository stockMovementRepository;
    private FinanceOperationalPostingService financePostingService;
    private CategoryService categoryService;
    private BranchTaxonomyResolver branchTaxonomyResolver;
    private InventoryProductReceiptService service;
    private final AtomicReference<String> capturedHash = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        receiptRepository = mock(DbInventoryProductReceiptRepository.class);
        productCommandRepository = mock(DbPosProductCommandRepository.class);
        productUnitRepository = mock(DbInventoryProductUnitRepository.class);
        stockMovementRepository = mock(DbInventoryStockMovementRepository.class);
        financePostingService = mock(FinanceOperationalPostingService.class);
        categoryService = mock(CategoryService.class);
        branchTaxonomyResolver = mock(BranchTaxonomyResolver.class);
        service = new InventoryProductReceiptService(
                receiptRepository,
                productCommandRepository,
                productUnitRepository,
                stockMovementRepository,
                financePostingService,
                categoryService,
                branchTaxonomyResolver,
                new ObjectMapper());

        // Capture the request hash written by the service and echo it back so
        // the idempotency comparison passes for fresh requests.
        when(receiptRepository.insertPendingIdempotency(anyInt(), anyInt(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    capturedHash.set(invocation.getArgument(4));
                    return true;
                });
        when(receiptRepository.findIdempotencyForUpdate(anyInt(), anyInt(), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.of(new DbInventoryProductReceiptRepository.IdempotencyRecord(
                        1L, capturedHash.get(), "PENDING", null, "op-1")));

        when(receiptRepository.findProductForUpdate(eq(COMPANY_ID), anyLong()))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.ProductReceiptProductSnapshot(
                        5L, "iPhone 13", "SKU-5", "BAR-5", TrackingType.QUANTITY, "MOBILE", "mobile_device")));
        when(receiptRepository.findActiveTemplate(eq(COMPANY_ID), anyString(), anyString()))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.InventoryTemplateDefinition(
                        1L, "MOBILE", "mobile_device", true, false, false, false, true)));
        when(receiptRepository.increaseBranchStockBalance(anyInt(), anyInt(), anyLong(), anyInt()))
                .thenReturn(new DbInventoryProductReceiptRepository.StockBalanceResult(0, 2));
        when(receiptRepository.insertReceiptLedger(anyInt(), anyInt(), anyLong(), anyInt(), anyInt(), any(), any(), any(),
                any(), anyString(), anyString(), any(), anyString(), any(), anyString(), any()))
                .thenReturn(42L);
        when(receiptRepository.updateSupplierPurchaseTotals(anyInt(), anyInt(), anyInt(), any(), any())).thenReturn(1);
        when(receiptRepository.insertLegacyInventoryTransaction(anyInt(), anyInt(), anyLong(), any(), anyInt(), anyInt(),
                any(), any(), any(), any())).thenReturn(77);
        when(receiptRepository.insertClientTradeInReceipt(anyInt(), anyInt(), anyInt(), anyLong(), anyLong(), anyString(),
                anyInt(), anyString(), any(), any(), any(), any(), any(), anyString(), any(), anyString(), any()))
                .thenReturn(9L);
        when(receiptRepository.findClientForUpdate(COMPANY_ID, 88))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.ClientSnapshot(88, "Ahmed", "0100", "ACTIVE")));
        when(productUnitRepository.insertProductUnit(any())).thenReturn(500L);
    }

    private ProductReceiptRequest baseRequest() {
        ProductReceiptRequest request = new ProductReceiptRequest();
        request.setCompanyId(COMPANY_ID);
        request.setBranchId(BRANCH_ID);
        request.setOperationMode(ProductReceiptOperationMode.RECEIVE_EXISTING_PRODUCT);
        request.setExistingProductId(5L);
        request.setIdempotencyKey("receipt-key-1");
        ProductReceiptDetailsRequest receipt = new ProductReceiptDetailsRequest();
        receipt.setQuantity(2);
        receipt.setUnitCost(new BigDecimal("100"));
        receipt.setPaidAmount(BigDecimal.ZERO);
        request.setReceipt(receipt);
        return request;
    }

    @Test
    void supplierFlowRemainsDefaultAndUpdatesSupplierTotals() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setSupplierId(1000);
        request.getReceipt().setPaymentMethod("Cash");

        ProductReceiptResponse response = service.receiveProduct("cashier", request);

        assertEquals("SUPPLIER", response.getReceipt().getAcquisitionSource());
        assertEquals("NEW", response.getReceipt().getConditionCode());
        verify(receiptRepository).updateSupplierPurchaseTotals(eq(COMPANY_ID), eq(BRANCH_ID), eq(1000), any(), any());
        verify(receiptRepository).insertLegacyInventoryTransaction(anyInt(), anyInt(), anyLong(), any(), eq(1000),
                anyInt(), any(), any(), any(), any());
        verify(financePostingService).enqueuePurchaseInventoryTransaction(eq(COMPANY_ID), eq(BRANCH_ID), any(), eq(77), eq(42L));
        verify(receiptRepository, never()).insertClientTradeInReceipt(anyInt(), anyInt(), anyInt(), anyLong(), anyLong(),
                anyString(), anyInt(), anyString(), any(), any(), any(), any(), any(), anyString(), any(), anyString(), any());
    }

    @Test
    void clientTradeInWritesPayableAndSkipsSupplierPath() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);
        request.getReceipt().setClientId(88);
        request.getReceipt().setConditionCode("USED");
        request.getReceipt().setPaymentOption("LATER");

        ProductReceiptResponse response = service.receiveProduct("cashier", request);

        assertEquals("CLIENT_TRADE_IN", response.getReceipt().getAcquisitionSource());
        assertEquals(Integer.valueOf(88), response.getReceipt().getClientId());
        assertEquals("USED", response.getReceipt().getConditionCode());
        assertEquals("UNPAID", response.getReceipt().getPaymentStatus());

        // Ledger row carries the client party and condition.
        verify(receiptRepository).insertReceiptLedger(eq(COMPANY_ID), eq(BRANCH_ID), eq(5L), eq(2), eq(0), any(), any(),
                any(), any(), anyString(), anyString(), any(), eq("CLIENT"), eq(88), eq("USED"), any());
        // Payable subledger row: total 200, paid 0, remaining 200, UNPAID.
        verify(receiptRepository).insertClientTradeInReceipt(eq(COMPANY_ID), eq(BRANCH_ID), eq(88), eq(42L), eq(5L),
                anyString(), eq(2), eq("USED"), any(), any(),
                eq(new BigDecimal("200.0000")), eq(new BigDecimal("0.0000")), eq(new BigDecimal("200.0000")),
                eq("UNPAID"), any(), anyString(), any());
        verify(financePostingService).enqueueClientTradeInReceipt(eq(COMPANY_ID), eq(BRANCH_ID), eq(88), eq(9L), eq(5L),
                eq(42L), eq(2), eq(new BigDecimal("200.0000")), eq(new BigDecimal("0.0000")), any(), any(), any());
        verify(receiptRepository, never()).updateSupplierPurchaseTotals(anyInt(), anyInt(), anyInt(), any(), any());
        verify(receiptRepository, never()).insertLegacyInventoryTransaction(anyInt(), anyInt(), anyLong(), any(),
                anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void serializedTradeInUnitsCarryClientAndCondition() {
        when(receiptRepository.findProductForUpdate(eq(COMPANY_ID), anyLong()))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.ProductReceiptProductSnapshot(
                        5L, "iPhone 13", "SKU-5", "BAR-5", TrackingType.IMEI, "MOBILE", "mobile_device")));

        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setQuantity(1);
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);
        request.getReceipt().setClientId(88);
        request.getReceipt().setConditionCode("USED");
        SerializedUnitInput unit = new SerializedUnitInput();
        unit.setImei("490154203237518");
        request.setSerializedUnits(List.of(unit));

        service.receiveProduct("cashier", request);

        ArgumentCaptor<ProductUnit> unitCaptor = ArgumentCaptor.forClass(ProductUnit.class);
        verify(productUnitRepository).insertProductUnit(unitCaptor.capture());
        ProductUnit saved = unitCaptor.getValue();
        assertEquals("USED", saved.getConditionCode());
        assertEquals("CLIENT", saved.getSourcePartyType());
        assertEquals(Long.valueOf(88), saved.getSourceClientId());
        assertEquals(null, saved.getSupplierId());
        assertEquals(new BigDecimal("100.0000"), saved.getAcquisitionCost());
    }

    @Test
    void tradeInWithoutClientIdIsRejected() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals("CLIENT_ID_REQUIRED", exception.getCode());
    }

    @Test
    void supplierFlowRejectsClientId() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setSupplierId(1000);
        request.getReceipt().setClientId(88);

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals("CLIENT_NOT_ALLOWED_FOR_SUPPLIER", exception.getCode());
    }

    @Test
    void archivedClientCannotSellToTheShop() {
        when(receiptRepository.findClientForUpdate(COMPANY_ID, 88))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.ClientSnapshot(88, "Ahmed", "0100", "ARCHIVED")));
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);
        request.getReceipt().setClientId(88);

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals("CLIENT_NOT_ACTIVE", exception.getCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void invalidConditionCodeIsRejected() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setSupplierId(1000);
        request.getReceipt().setConditionCode("REFURBISHED");

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals("CONDITION_CODE_INVALID", exception.getCode());
    }

    @Test
    void fullPaymentOptionRequiresExactAmount() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);
        request.getReceipt().setClientId(88);
        request.getReceipt().setPaymentOption("FULL");
        request.getReceipt().setPaymentMethod("cash");
        request.getReceipt().setPaidAmount(new BigDecimal("150")); // total is 200

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals("PAYMENT_FULL_AMOUNT_MISMATCH", exception.getCode());
    }

    @Test
    void payLaterRejectsAnyPaidAmount() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);
        request.getReceipt().setClientId(88);
        request.getReceipt().setPaymentOption("LATER");
        request.getReceipt().setPaidAmount(new BigDecimal("10"));

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals("PAYMENT_LATER_AMOUNT_INVALID", exception.getCode());
    }

    @Test
    void partialPaymentMarksReceiptPartiallyPaid() {
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setAcquisitionSource(AcquisitionSource.CLIENT_TRADE_IN);
        request.getReceipt().setClientId(88);
        request.getReceipt().setPaymentOption("PARTIAL");
        request.getReceipt().setPaymentMethod("cash");
        request.getReceipt().setPaidAmount(new BigDecimal("50"));

        ProductReceiptResponse response = service.receiveProduct("cashier", request);

        assertEquals("PARTIALLY_PAID", response.getReceipt().getPaymentStatus());
        verify(receiptRepository).insertClientTradeInReceipt(eq(COMPANY_ID), eq(BRANCH_ID), eq(88), eq(42L), eq(5L),
                anyString(), eq(2), eq("NEW"), any(), any(),
                eq(new BigDecimal("200.0000")), eq(new BigDecimal("50.0000")), eq(new BigDecimal("150.0000")),
                eq("PARTIALLY_PAID"), eq("cash"), anyString(), any());
    }

    @Test
    void duplicateIdempotentRequestWithDifferentPayloadReturnsConflict() {
        when(receiptRepository.findIdempotencyForUpdate(anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.IdempotencyRecord(
                        1L, "a-different-request-hash", "PENDING", null, "op-1")));
        ProductReceiptRequest request = baseRequest();
        request.getReceipt().setSupplierId(1000);

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("cashier", request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("IDEMPOTENCY_KEY_PAYLOAD_CONFLICT", exception.getCode());
    }
}
