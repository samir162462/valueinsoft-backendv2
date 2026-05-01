package com.example.valueinsoftbackend.Model.Payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunLineComponent {
    private long id;
    private int companyId;
    private long payrollRunLineId;
    private String componentType;  // ALLOWANCE | DEDUCTION
    private Integer typeId;
    private String typeCode;
    private String typeName;
    private String calcMethod;     // FIXED | PERCENTAGE
    private BigDecimal amount;
    private String source;         // PROFILE | ADJUSTMENT | ATTENDANCE
}
