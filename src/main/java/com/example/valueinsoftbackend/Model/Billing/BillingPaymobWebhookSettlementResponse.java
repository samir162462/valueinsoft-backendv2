package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymobWebhookSettlementResponse {
    private String status;
    private String providerCode;
    private String providerEventId;
    private String externalOrderId;
    private Long billingInvoiceId;
    private Long billingPaymentAttemptId;
    private Long billingPaymentId;
    private Long billingPaymentAllocationId;
    private boolean duplicate;
}
