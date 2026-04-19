package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditRow {
    private Long productId;
    private String productName;
    private String category;
    private String branch;
    private Integer openingQty;
    private Integer inQty;
    private Integer outQty;
    private Integer closingQty;
    private Integer unitPrice;
    private Long totalValue;
    private Timestamp lastMovementDate;
}
