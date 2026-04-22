package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceAccountItem {
    private UUID accountId;
    private int companyId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private String normalBalance;
    private UUID parentAccountId;
    private String accountPath;
    private int accountLevel;
    private boolean postable;
    private boolean system;
    private String status;
    private String currencyCode;
    private boolean requiresBranch;
    private boolean requiresCustomer;
    private boolean requiresSupplier;
    private boolean requiresProduct;
    private boolean requiresCostCenter;
    private int version;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
