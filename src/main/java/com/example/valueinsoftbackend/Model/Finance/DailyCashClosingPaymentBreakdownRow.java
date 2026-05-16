package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingPaymentBreakdownRow {
    private String paymentMethod;
    private long invoiceCount;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal returnAmount;
    private BigDecimal netAmount;
}
