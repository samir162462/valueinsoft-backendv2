package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRevenueTrendPoint {
    private Date metricDate;
    private BigDecimal salesAmount;
    private BigDecimal expenseAmount;
    private BigDecimal collectedAmount;
    private BigDecimal netAmount;
    private int tenantCountRepresented;
}
