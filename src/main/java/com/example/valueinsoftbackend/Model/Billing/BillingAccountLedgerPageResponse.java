package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccountLedgerPageResponse {
    private int companyId;
    private String currencyCode;
    private int page;
    private int size;
    private List<BillingAccountLedgerItem> items;
}
