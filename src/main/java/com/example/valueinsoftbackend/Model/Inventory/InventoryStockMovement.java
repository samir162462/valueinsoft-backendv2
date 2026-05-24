package com.example.valueinsoftbackend.Model.Inventory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockMovement {
    private long stockMovementId;
    private long companyId;
    private Long branchId;
    private long productId;
    private Long productUnitId;
    private InventoryMovementType movementType;
    private BigDecimal quantityDelta;
    private Long fromBranchId;
    private Long toBranchId;
    private String referenceType;
    private String referenceId;
    private Long referenceLineId;
    private Long supplierId;
    private Long customerId;
    private Long actorUserId;
    private String actorName;
    private String note;
    private String idempotencyKey;
    private Timestamp createdAt;
}
