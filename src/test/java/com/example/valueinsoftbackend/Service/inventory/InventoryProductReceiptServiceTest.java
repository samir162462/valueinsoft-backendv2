package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductReceiptRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductUnitRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryStockMovementRepository;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosProductCommandRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptDetailsRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptOperationMode;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptProductRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptRequest;
import com.example.valueinsoftbackend.Service.CategoryService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryProductReceiptServiceTest {

    @Test
    void invalidClassificationDoesNotCreateProductStockOrLedger() {
        ObjectMapper objectMapper = new ObjectMapper();
        DbInventoryProductReceiptRepository receiptRepository = mock(DbInventoryProductReceiptRepository.class);
        DbPosProductCommandRepository productCommandRepository = mock(DbPosProductCommandRepository.class);
        BranchTaxonomyResolver branchTaxonomyResolver = mock(BranchTaxonomyResolver.class);

        ProductReceiptRequest request = createRequest();
        when(receiptRepository.findIdempotencyForUpdate(anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(Optional.of(new DbInventoryProductReceiptRepository.IdempotencyRecord(
                        1L,
                        hashRequest(objectMapper, request),
                        "PENDING",
                        null,
                        "operation-1"
                )));
        when(branchTaxonomyResolver.resolveForProduct(anyInt(), anyInt(), any(ProductReceiptProductRequest.class)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "CLASSIFICATION_CATEGORY_INVALID", "Selected category does not belong to the selected group"));

        InventoryProductReceiptService service = new InventoryProductReceiptService(
                receiptRepository,
                productCommandRepository,
                mock(DbInventoryProductUnitRepository.class),
                mock(DbInventoryStockMovementRepository.class),
                mock(FinanceOperationalPostingService.class),
                mock(CategoryService.class),
                branchTaxonomyResolver,
                objectMapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.receiveProduct("tester", request));

        assertEquals("CLASSIFICATION_CATEGORY_INVALID", exception.getCode());
        verify(productCommandRepository, never()).addProduct(any(), anyString(), anyInt());
        verify(receiptRepository, never()).increaseBranchStockBalance(anyInt(), anyInt(), anyLong(), anyInt());
        verify(receiptRepository, never()).insertReceiptLedger(
                anyInt(), anyInt(), anyLong(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), anyString(), anyString(), anyString());
    }

    private ProductReceiptRequest createRequest() {
        ProductReceiptProductRequest product = new ProductReceiptProductRequest();
        product.setProductName("iPhone 16 Pro");
        product.setBuyingPrice(BigDecimal.valueOf(10));
        product.setLowestPrice(BigDecimal.valueOf(12));
        product.setRetailPrice(BigDecimal.valueOf(15));
        product.setBusinessLineKey("MOBILE");
        product.setTemplateKey("mobile_device");

        ProductReceiptDetailsRequest receipt = new ProductReceiptDetailsRequest();
        receipt.setSupplierId(44);
        receipt.setQuantity(1);
        receipt.setUnitCost(BigDecimal.valueOf(10));
        receipt.setPaidAmount(BigDecimal.ZERO);
        receipt.setPaymentMethod("Cash");

        ProductReceiptRequest request = new ProductReceiptRequest();
        request.setCompanyId(1095);
        request.setBranchId(1074);
        request.setOperationMode(ProductReceiptOperationMode.CREATE_PRODUCT_AND_RECEIVE);
        request.setProduct(product);
        request.setReceipt(receipt);
        request.setIdempotencyKey("invalid-classification-test");
        return request;
    }

    private String hashRequest(ObjectMapper objectMapper, ProductReceiptRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
