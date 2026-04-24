package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
public class FinanceReconciliationProofUploadRequest {
    private int companyId;
    @NotBlank
    private String fileName;
    @NotBlank
    private String contentType;
    @Positive
    private long fileSize;
}
