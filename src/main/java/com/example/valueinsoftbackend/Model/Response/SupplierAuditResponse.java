package com.example.valueinsoftbackend.Model.Response;

import java.util.List;

public class SupplierAuditResponse {

    private final int supplierId;
    private final int page;
    private final int size;
    private final List<SupplierAuditEventResponse> events;

    public SupplierAuditResponse(int supplierId, int page, int size, List<SupplierAuditEventResponse> events) {
        this.supplierId = supplierId;
        this.page = page;
        this.size = size;
        this.events = events;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public List<SupplierAuditEventResponse> getEvents() {
        return events;
    }
}
