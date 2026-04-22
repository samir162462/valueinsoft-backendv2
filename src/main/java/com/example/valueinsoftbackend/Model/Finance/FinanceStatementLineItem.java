package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceStatementLineItem {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private String normalBalance;
    private String accountPath;
    private int accountLevel;
    private BigDecimal amount;
}
