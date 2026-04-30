package com.example.valueinsoftbackend.Model.Response;

import java.math.BigDecimal;
import java.time.Instant;

public class SupplierOpenDocumentResponse {

    private final String sourceType;
    private final String sourceId;
    private final String sourceNumber;
    private final Instant documentDate;
    private final BigDecimal openAmount;
    private final int ageDays;
    private final String bucket;

    public SupplierOpenDocumentResponse(String sourceType,
                                        String sourceId,
                                        String sourceNumber,
                                        Instant documentDate,
                                        BigDecimal openAmount,
                                        int ageDays,
                                        String bucket) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceNumber = sourceNumber;
        this.documentDate = documentDate;
        this.openAmount = openAmount;
        this.ageDays = ageDays;
        this.bucket = bucket;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSourceNumber() {
        return sourceNumber;
    }

    public Instant getDocumentDate() {
        return documentDate;
    }

    public BigDecimal getOpenAmount() {
        return openAmount;
    }

    public int getAgeDays() {
        return ageDays;
    }

    public String getBucket() {
        return bucket;
    }
}
