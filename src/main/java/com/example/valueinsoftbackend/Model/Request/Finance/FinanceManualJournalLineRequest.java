package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FinanceManualJournalLineRequest {
    @NotNull
    private UUID accountId;

    @Positive
    private Integer branchId;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @NotNull
    @DecimalMin(value = "0.0000")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    private String description;

    private Integer customerId;

    private Integer supplierId;

    private Long productId;

    private Long inventoryMovementId;

    private String paymentId;

    private UUID costCenterId;

    private UUID taxCodeId;
}
