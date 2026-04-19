package com.example.valueinsoftbackend.Model.Request.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditSearchRequest {
    @NotNull
    private Integer companyId;

    @NotNull
    private Integer branchId;

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    private String query;
    private Long productId;
    private String category;
    private String major;
    private String businessLineKey;
    private String templateKey;
    private Integer supplierId;
    private Integer lowStockThreshold;
    private Boolean lowStockOnly = false;
    private String groupBy = "NONE";
    private Integer page = 1;
    private Integer size = 25;
    private String sortField = "lastMovementDate";
    private String sortDirection = "desc";
}
