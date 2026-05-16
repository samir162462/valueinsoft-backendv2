package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingSummary {
    private BigDecimal grossSales;
    private BigDecimal totalDiscounts;
    private BigDecimal totalReturnsRefunds;
    private BigDecimal netSales;
    private BigDecimal cashSales;
    private BigDecimal cardSales;
    private BigDecimal walletSales;
    private BigDecimal creditSales;
    private BigDecimal totalExpenses;
    private BigDecimal openingCash;
    private BigDecimal cashIn;
    private BigDecimal cashRefunds;
    private BigDecimal cashExpenses;
    private BigDecimal cashOut;
    private BigDecimal expectedCash;
    private BigDecimal actualCountedCash;
    private BigDecimal cashDifference;
    private long totalInvoices;
    private long totalReturns;
}
