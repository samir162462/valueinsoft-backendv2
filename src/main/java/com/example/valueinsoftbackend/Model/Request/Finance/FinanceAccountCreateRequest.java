package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class FinanceAccountCreateRequest {
    @Positive
    private int companyId;

    @NotBlank
    private String accountCode;

    @NotBlank
    private String accountName;

    @NotBlank
    private String accountType;

    @NotBlank
    private String normalBalance;

    private UUID parentAccountId;

    private boolean postable = true;

    private boolean system = false;

    @NotBlank
    private String status = "active";

    @Pattern(regexp = "^[A-Z]{3}$")
    private String currencyCode;

    private boolean requiresBranch;
    private boolean requiresCustomer;
    private boolean requiresSupplier;
    private boolean requiresProduct;
    private boolean requiresCostCenter;
}
