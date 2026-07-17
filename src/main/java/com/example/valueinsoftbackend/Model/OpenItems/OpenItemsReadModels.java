package com.example.valueinsoftbackend.Model.OpenItems;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class OpenItemsReadModels {

    private OpenItemsReadModels() {
    }

    public record OpenItem(
            long openItemId,
            int companyId,
            int branchId,
            int partyId,
            String sourceType,
            Long sourceId,
            String documentRef,
            LocalDateTime documentDate,
            LocalDateTime dueDate,
            String currencyCode,
            BigDecimal totalAmount,
            BigDecimal settledAmount,
            BigDecimal remainingAmount,
            String status,
            String notes) {
    }

    public record OpenItemPage(List<OpenItem> items, int limit, int offset, long totalItems) {
    }

    public record StatementLine(
            LocalDateTime eventDate,
            String eventType,
            long sourceId,
            String documentRef,
            String currencyCode,
            BigDecimal amount,
            BigDecimal runningBalance) {
    }

    public record Statement(
            int partyId,
            int branchId,
            LocalDate fromDate,
            LocalDate toDate,
            Map<String, BigDecimal> openingBalance,
            List<StatementLine> lines,
            Map<String, BigDecimal> closingBalance) {
    }

    public record AgingBucket(
            String currencyCode,
            BigDecimal current,
            BigDecimal days1To30,
            BigDecimal days31To60,
            BigDecimal days61To90,
            BigDecimal over90,
            BigDecimal total) {
    }

    public record Aging(int partyId, int branchId, LocalDate asOfDate, List<AgingBucket> currencies) {
    }

    public record ClientCredit(
            int clientId,
            int branchId,
            String currencyCode,
            BigDecimal creditLimit,
            int creditTermsDays,
            String creditStatus,
            String creditNotes,
            BigDecimal openExposure,
            BigDecimal availableCredit) {
    }

    public record ReceiptLock(
            int receiptId,
            int branchId,
            int partyId,
            BigDecimal amount,
            String status) {
    }
}
