package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
public class InventoryAuditPageResponse {
    private ArrayList<InventoryAuditRow> rows = new ArrayList<>();
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private InventoryAuditSummary summary = new InventoryAuditSummary();
    private ArrayList<InventoryAuditGroupSummary> grouping = new ArrayList<>();
    private boolean costVisible = true;

    public InventoryAuditPageResponse(ArrayList<InventoryAuditRow> rows,
                                      int page,
                                      int size,
                                      long totalItems,
                                      int totalPages,
                                      InventoryAuditSummary summary,
                                      ArrayList<InventoryAuditGroupSummary> grouping) {
        this.rows = rows;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
        this.summary = summary;
        this.grouping = grouping;
    }
}
