package com.example.valueinsoftbackend.Model.AppModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchBillingCheckoutResponse {
    private long billingInvoiceId;
    private long branchSubscriptionId;
    private String providerCode;
    private String orderId;
    private String checkoutUrl;
    private BigDecimal dueAmount;
    private String currencyCode;
}
