package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingExpenseRow {
    private int expenseNo;
    private String expenseType;
    private LocalDateTime dateTime;
    private String paidBy;
    private String paymentMethod;
    private BigDecimal amount;
    private String notes;
}
