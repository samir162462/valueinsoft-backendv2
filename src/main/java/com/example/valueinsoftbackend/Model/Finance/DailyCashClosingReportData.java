package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashClosingReportData {
    private int companyId;
    private int branchId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String generatedBy;
    private Instant generatedAt;
    private DailyCashClosingHeader header;
    private DailyCashClosingSummary summary;
    private ArrayList<DailyCashClosingPaymentBreakdownRow> paymentBreakdown;
    private ArrayList<DailyCashClosingCashMovementRow> cashMovements;
    private ArrayList<DailyCashClosingInvoiceRow> invoices;
    private ArrayList<DailyCashClosingExpenseRow> expenses;
}
