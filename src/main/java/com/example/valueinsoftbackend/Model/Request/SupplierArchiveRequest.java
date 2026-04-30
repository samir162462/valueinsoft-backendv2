package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Size;

public class SupplierArchiveRequest {

    @Size(max = 500, message = "reason must be 500 characters or fewer")
    private String reason;

    private Integer replacementSupplierId;

    public SupplierArchiveRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getReplacementSupplierId() {
        return replacementSupplierId;
    }

    public void setReplacementSupplierId(Integer replacementSupplierId) {
        this.replacementSupplierId = replacementSupplierId;
    }
}
