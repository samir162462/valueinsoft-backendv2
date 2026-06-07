package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApplyRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceAdjustmentBatchRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceHistoryRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAdjustmentApplyServiceTest {

    @Mock
    private PriceAdjustmentBatchRepository batchRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private DynamicPricingSecurityService securityService;

    @Mock
    private PricingAuditService auditService;

    @InjectMocks
    private PriceAdjustmentApplyService service;

    @Test
    void applyRejectsBatchThatIsNotApproved() {
        when(batchRepository.lockBatch(1, 99L)).thenReturn(batch("PENDING_APPROVAL"));

        assertThrows(ApiException.class, () -> service.apply("owner@example.com", 1, 2, 99L, null));

        verify(securityService).requireAdjustmentApply("owner@example.com", 1, 2);
    }

    @Test
    void applySkipsStaleProductPrice() {
        var item = item();
        when(batchRepository.lockBatch(1, 99L)).thenReturn(batch("APPROVED"));
        when(batchRepository.findApplicableItems(1, 2, 99L)).thenReturn(List.of(item));
        when(batchRepository.findProductPrice(1, 10L)).thenReturn(new PriceAdjustmentBatchRepository.ProductPriceRow(
                10L,
                new BigDecimal("101.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("60.0000")
        ));

        var response = service.apply("owner@example.com", 1, 2, 99L, new PriceAdjustmentApplyRequest("apply"));

        assertEquals("FAILED", response.status());
        assertEquals(1, response.skippedItems());
        verify(batchRepository).markItemSkipped(1, 100L, "Product price changed after preview");
        verify(priceHistoryRepository, never()).insertAppliedPriceChange(org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.anyLong(), org.mockito.Mockito.any(), org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString());
    }

    @Test
    void applyUpdatesProductAndWritesHistory() {
        var item = item();
        when(batchRepository.lockBatch(1, 99L)).thenReturn(batch("APPROVED"));
        when(batchRepository.findApplicableItems(1, 2, 99L)).thenReturn(List.of(item));
        when(batchRepository.findProductPrice(1, 10L)).thenReturn(new PriceAdjustmentBatchRepository.ProductPriceRow(
                10L,
                new BigDecimal("100.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("60.0000")
        ));
        when(batchRepository.updateProductPriceIfCurrent(1, item)).thenReturn(1);

        var response = service.apply("owner@example.com", 1, 2, 99L, new PriceAdjustmentApplyRequest("apply"));

        assertEquals("APPLIED", response.status());
        assertEquals(1, response.appliedItems());
        verify(priceHistoryRepository).insertAppliedPriceChange(1, 2, 99L, item, "owner@example.com", "apply");
        verify(batchRepository).markItemApplied(1, 100L);
    }

    @Test
    void applySkipsWhenCurrentBuyingPriceMakesPreviewBelowCost() {
        var item = item();
        when(batchRepository.lockBatch(1, 99L)).thenReturn(batch("APPROVED"));
        when(batchRepository.findApplicableItems(1, 2, 99L)).thenReturn(List.of(item));
        when(batchRepository.findProductPrice(1, 10L)).thenReturn(new PriceAdjustmentBatchRepository.ProductPriceRow(
                10L,
                new BigDecimal("100.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("106.0000")
        ));

        var response = service.apply("owner@example.com", 1, 2, 99L, new PriceAdjustmentApplyRequest("apply"));

        assertEquals("FAILED", response.status());
        assertEquals(1, response.skippedItems());
        verify(batchRepository).markItemSkipped(1, 100L, "New price is below current buying price");
        verify(batchRepository, never()).updateProductPriceIfCurrent(1, item);
    }

    private PriceAdjustmentBatchResponse batch(String status) {
        return new PriceAdjustmentBatchResponse(
                99L,
                1,
                2,
                "BULK_MANUAL",
                null,
                status,
                "PERCENTAGE",
                "INCREASE",
                BigDecimal.ONE,
                "[\"RETAIL\"]",
                1,
                1,
                0,
                0,
                0,
                0,
                "test",
                "creator@example.com",
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                null,
                null,
                null,
                null,
                OffsetDateTime.now()
        );
    }

    private PriceAdjustmentBatchRepository.ApplyItemRow item() {
        return new PriceAdjustmentBatchRepository.ApplyItemRow(
                100L,
                99L,
                10L,
                null,
                "Product",
                new BigDecimal("100.0000"),
                new BigDecimal("105.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("60.0000"),
                new BigDecimal("5.0000"),
                new BigDecimal("0.0500"),
                "VALID"
        );
    }
}
