package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditPageResponse {
    private ArrayList<InventoryAuditRow> rows = new ArrayList<>();
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private InventoryAuditSummary summary = new InventoryAuditSummary();
    private ArrayList<InventoryAuditGroupSummary> grouping = new ArrayList<>();
}
