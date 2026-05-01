package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollPaymentLine {
    private long id;
    private int companyId;
    private long payrollPaymentId;
    private long payrollRunLineId;
    private int employeeId;
    private BigDecimal netSalary;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private String paymentMethod;    // per-employee override (CASH | BANK | etc.)
    private String paymentStatus;    // UNPAID | PARTIALLY_PAID | PAID
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
