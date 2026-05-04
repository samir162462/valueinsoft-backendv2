package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.model.OfflineValidationProductSnapshot;
import com.example.valueinsoftbackend.pos.offline.model.PosDeviceModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderValidationRepository;
import com.example.valueinsoftbackend.pos.offline.repository.PosDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineOrderImportValidationServiceTest {

    @Mock
    private OfflineOrderValidationRepository validationRepo;

    @Mock
    private PosDeviceRepository deviceRepo;

    @Mock
    private PosIdempotencyService idempotencyService;

    @InjectMocks
    private OfflineOrderImportValidationService validationService;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long DEVICE_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idem-123";
    private static final String PAYLOAD_HASH = "hash-123";

    @BeforeEach
    void setUp() {
        // Default mocks for basic tenant checks
        lenient().when(validationRepo.branchBelongsToCompany(COMPANY_ID, BRANCH_ID)).thenReturn(true);
        lenient().when(deviceRepo.findById(eq(COMPANY_ID), eq(BRANCH_ID), eq(DEVICE_ID)))
                .thenReturn(Optional.of(mock(PosDeviceModel.class)));
    }

    @Test
    void validateSuccess() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "subtotalAmount": 100.00,
                  "discountAmount": 10.00,
                  "taxAmount": 13.50,
                  "totalAmount": 103.50,
                  "items": [
                    {
                      "productId": 50,
                      "quantity": 2,
                      "unitPrice": 50.00,
                      "discountAmount": 10.00,
                      "taxAmount": 13.50,
                      "lineTotal": 103.50
                    }
                  ],
                  "payments": [
                    { "amount": 103.50, "paymentMethod": "CASH" }
                  ]
                }
                """;

        OfflineOrderImportModel importModel = createImportModel(payload);
        
        when(validationRepo.findProductsForValidation(eq(COMPANY_ID), eq(BRANCH_ID), anySet(), anySet()))
                .thenReturn(List.of(new OfflineValidationProductSnapshot(50L, null, new BigDecimal("50.00"), new BigDecimal("40.00"), true, true)));

        List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importModel);

        assertTrue(errors.isEmpty(), "Should have no errors but found: " + errors);
    }

    @Test
    void validateBranchMismatch() {
        when(validationRepo.branchBelongsToCompany(COMPANY_ID, BRANCH_ID)).thenReturn(false);

        OfflineOrderImportModel importModel = createImportModel("{}");
        List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importModel);

        assertHasError(errors, "INVALID_TENANT_ACCESS");
    }

    @Test
    void validateDeviceNotFound() {
        when(deviceRepo.findById(eq(COMPANY_ID), eq(BRANCH_ID), eq(DEVICE_ID))).thenReturn(Optional.empty());

        OfflineOrderImportModel importModel = createImportModel("{}");
        List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importModel);

        assertHasError(errors, "OFFLINE_DEVICE_NOT_FOUND");
    }

    @Test
    void validateTotalMismatch() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "subtotalAmount": 100.00,
                  "discountAmount": 0.00,
                  "taxAmount": 0.00,
                  "totalAmount": 90.00,
                  "items": [
                    {
                      "productId": 50,
                      "quantity": 1,
                      "unitPrice": 100.00,
                      "discountAmount": 0.00,
                      "taxAmount": 0.00,
                      "lineTotal": 100.00
                    }
                  ]
                }
                """;

        OfflineOrderImportModel importModel = createImportModel(payload);
        
        when(validationRepo.findProductsForValidation(anyLong(), anyLong(), anySet(), anySet()))
                .thenReturn(List.of(new OfflineValidationProductSnapshot(50L, null, new BigDecimal("100.00"), null, true, true)));

        List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importModel);

        assertHasError(errors, "OFFLINE_TOTAL_MISMATCH");
    }

    @Test
    void validateProductNotFound() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "subtotalAmount": 100.00,
                  "totalAmount": 100.00,
                  "items": [
                    { "productId": 999, "quantity": 1, "unitPrice": 100.00, "lineTotal": 100.00 }
                  ]
                }
                """;

        OfflineOrderImportModel importModel = createImportModel(payload);
        when(validationRepo.findProductsForValidation(anyLong(), anyLong(), anySet(), anySet())).thenReturn(List.of());

        List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importModel);

        assertHasError(errors, "OFFLINE_PRODUCT_NOT_FOUND");
    }

    @Test
    void validatePaymentMismatch() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "subtotalAmount": 100.00,
                  "totalAmount": 100.00,
                  "items": [
                    { "productId": 50, "quantity": 1, "unitPrice": 100.00, "lineTotal": 100.00 }
                  ],
                  "payments": [
                    { "amount": 50.00 }
                  ]
                }
                """;

        OfflineOrderImportModel importModel = createImportModel(payload);
        when(validationRepo.findProductsForValidation(anyLong(), anyLong(), anySet(), anySet()))
                .thenReturn(List.of(new OfflineValidationProductSnapshot(50L, null, new BigDecimal("100.00"), null, true, true)));

        List<OfflineOrderImportValidationService.ValidationError> errors = validationService.validate(importModel);

        assertHasError(errors, "OFFLINE_PAYMENT_TOTAL_MISMATCH");
    }

    private OfflineOrderImportModel createImportModel(String payloadJson) {
        return new OfflineOrderImportModel(
                1L, 100L, COMPANY_ID, BRANCH_ID, DEVICE_ID, null,
                "ORD-001", IDEMPOTENCY_KEY, Instant.now(), payloadJson, PAYLOAD_HASH,
                OfflineOrderImportStatus.PENDING, null, null, null, null, 0,
                Instant.now(), null, null, Instant.now()
        );
    }

    private void assertHasError(List<OfflineOrderImportValidationService.ValidationError> errors, String expectedCode) {
        boolean found = errors.stream().anyMatch(e -> e.code().equals(expectedCode));
        assertTrue(found, "Expected error code " + expectedCode + " but found: " + errors);
    }
}
