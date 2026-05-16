package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingInvoiceRow {
    private int invoiceNo;
    private LocalDateTime dateTime;
    private String cashier;
    private String customer;
    private String paymentMethod;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal returnAmount;
    private BigDecimal netAmount;
    private String status;
}
