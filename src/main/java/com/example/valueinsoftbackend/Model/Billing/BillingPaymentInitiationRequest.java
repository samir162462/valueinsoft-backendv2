package com.example.valueinsoftbackend.Model.Billing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPaymentInitiationRequest {
    private String idempotencyKey;
    private Boolean checkoutFallbackEnabled;
}
