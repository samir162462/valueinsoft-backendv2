package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentApprovalRequest;
import com.example.valueinsoftbackend.pricing.dynamic.dto.PriceAdjustmentBatchResponse;
import com.example.valueinsoftbackend.pricing.dynamic.model.DynamicPricingPolicy;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceAdjustmentBatchRepository;
import com.example.valueinsoftbackend.pricing.dynamic.security.DynamicPricingSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAdjustmentApprovalServiceTest {

    @Mock
    private PriceAdjustmentBatchRepository repository;

    @Mock
    private DynamicPricingSecurityService securityService;

    @Mock
    private PricingAuditService auditService;

    @Mock
    private DynamicPricingPolicyRepository policyRepository;

    @InjectMocks
    private PriceAdjustmentApprovalService service;

    @Test
    void submitRejectsBatchWithBlockedItems() {
        when(repository.lockBatch(1, 99L)).thenReturn(batch("PREVIEWED", "creator@example.com", 1, 0, 1));

        assertThrows(ApiException.class, () -> service.submit("creator@example.com", 1, 2, 99L));

        verify(securityService).requireAdjustmentSubmit("creator@example.com", 1, 2);
    }

    @Test
    void approveRejectsCreatorAsApprover() {
        when(repository.lockBatch(1, 99L)).thenReturn(batch("PENDING_APPROVAL", "creator@example.com", 1, 0, 0));
        when(policyRepository.findEffectivePolicy(1, 2, null)).thenReturn(Optional.of(policy(true)));

        assertThrows(ApiException.class, () -> service.approve(
                "creator@example.com",
                1,
                2,
                99L,
                new PriceAdjustmentApprovalRequest("ok")
        ));

        verify(securityService).requireAdjustmentApprove("creator@example.com", 1, 2);
    }

    @Test
    void cancelAllowsPreviewedBatch() {
        when(repository.lockBatch(1, 99L)).thenReturn(batch("PREVIEWED", "creator@example.com", 1, 0, 0));
        when(repository.findBatch(1, 99L)).thenReturn(batch("CANCELLED", "creator@example.com", 1, 0, 0));

        service.cancel("manager@example.com", 1, 2, 99L);

        verify(repository).markCancelled(1, 99L);
        verify(auditService).log(1, 2, "ADJUSTMENT_BATCH_CANCELLED", "BATCH", "99",
                "manager@example.com", "Cancelled price adjustment batch", "{\"batchId\":99}");
    }

    private PriceAdjustmentBatchResponse batch(String status, String createdBy, int valid, int warning, int blocked) {
        return new PriceAdjustmentBatchResponse(
                99L,
                1,
                2,
                "BULK_MANUAL",
                null,
                status,
                "PERCENTAGE",
                "INCREASE",
                java.math.BigDecimal.ONE,
                "[\"RETAIL\"]",
                valid + warning + blocked,
                valid,
                warning,
                blocked,
                0,
                0,
                "test",
                createdBy,
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

    private DynamicPricingPolicy policy(boolean makerCheckerRequired) {
        return new DynamicPricingPolicy(
                1L,
                1L,
                2L,
                "BRANCH",
                null,
                "Test policy",
                new BigDecimal("0.2000"),
                new BigDecimal("0.1000"),
                new BigDecimal("0.0500"),
                new BigDecimal("0.1000"),
                null,
                null,
                null,
                null,
                false,
                true,
                makerCheckerRequired,
                false,
                null,
                500,
                "NEAREST_1",
                new BigDecimal("7.0000"),
                new BigDecimal("60.0000"),
                45,
                120,
                "{}",
                true,
                "system",
                null,
                null,
                null
        );
    }
}
