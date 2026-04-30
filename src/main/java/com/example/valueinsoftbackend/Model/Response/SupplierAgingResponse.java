package com.example.valueinsoftbackend.Model.Response;

import java.util.List;

public class SupplierAgingResponse {

    private final int supplierId;
    private final String supplierName;
    private final String currency;
    private final String asOfDate;
    private final SupplierAgingBucketResponse buckets;
    private final List<SupplierOpenDocumentResponse> openDocuments;

    public SupplierAgingResponse(int supplierId,
                                 String supplierName,
                                 String currency,
                                 String asOfDate,
                                 SupplierAgingBucketResponse buckets,
                                 List<SupplierOpenDocumentResponse> openDocuments) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.currency = currency;
        this.asOfDate = asOfDate;
        this.buckets = buckets;
        this.openDocuments = openDocuments;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAsOfDate() {
        return asOfDate;
    }

    public SupplierAgingBucketResponse getBuckets() {
        return buckets;
    }

    public List<SupplierOpenDocumentResponse> getOpenDocuments() {
        return openDocuments;
    }
}
