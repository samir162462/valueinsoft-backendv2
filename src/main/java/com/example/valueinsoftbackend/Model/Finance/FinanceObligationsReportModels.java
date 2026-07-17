package com.example.valueinsoftbackend.Model.Finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceObligationsReportModels {

    private FinanceObligationsReportModels() {
    }

    public record CurrencyTotal(String currencyCode, BigDecimal totalAmount,
                                BigDecimal settledAmount, BigDecimal remainingAmount,
                                BigDecimal overdueAmount, long partyCount, long documentCount) {
    }

    public record PartySummary(int partyId, String partyType, int branchId, String partyName,
                               String primaryPhone, String secondaryPhone, String location,
                               String currencyCode, BigDecimal totalAmount, BigDecimal settledAmount,
                               BigDecimal remainingAmount, BigDecimal overdueAmount,
                               LocalDateTime oldestDueDate, int openDocumentCount,
                               int overdueDocumentCount) {
    }

    public record SettlementDetail(long allocationId, String sourceType, long sourceId,
                                   String sourceReference, String paymentMethod, String recordedBy,
                                   LocalDateTime recordedAt, BigDecimal amount, String status,
                                   Long reversalOfAllocationId) {
    }

    public record SourceLine(long lineId, Long itemId, String itemName, BigDecimal quantity,
                             BigDecimal unitAmount, BigDecimal totalAmount, String notes) {
    }

    public record ObligationDocument(long openItemId, String sourceType, Long sourceId,
                                     String documentReference, LocalDateTime documentDate,
                                     LocalDateTime dueDate, String dueState, long daysOverdue,
                                     String currencyCode, BigDecimal totalAmount,
                                     BigDecimal settledAmount, BigDecimal remainingAmount,
                                     String status, String notes, String sourceReference,
                                     String paymentMethod, String sourceActor,
                                     LocalDateTime sourceDate, BigDecimal sourceGrossAmount,
                                     int sourceLineCount, List<SourceLine> sourceLines,
                                     List<SettlementDetail> settlements) {
    }

    public record Page(int companyId, int branchId, String side, LocalDate asOfDate,
                       List<CurrencyTotal> totals, List<PartySummary> parties,
                       int limit, int offset, long totalParties) {
    }

    public record PartyDetails(int companyId, int branchId, String side, LocalDate asOfDate,
                               PartySummary party, List<ObligationDocument> documents) {
    }
}
