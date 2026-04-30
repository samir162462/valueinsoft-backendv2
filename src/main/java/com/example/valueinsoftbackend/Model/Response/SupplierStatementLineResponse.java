package com.example.valueinsoftbackend.Model.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SupplierStatementLineResponse {

    private final Instant date;
    private final String sourceType;
    private final String sourceId;
    private final String sourceNumber;
    private final String description;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private BigDecimal balance;
    private final String postingStatus;
    private final UUID postingRequestId;
    private final UUID journalId;
    private final String postingFailureReason;

    public SupplierStatementLineResponse(Instant date,
                                         String sourceType,
                                         String sourceId,
                                         String sourceNumber,
                                         String description,
                                         BigDecimal debit,
                                         BigDecimal credit,
                                         BigDecimal balance,
                                         String postingStatus,
                                         UUID postingRequestId,
                                         UUID journalId,
                                         String postingFailureReason) {
        this.date = date;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceNumber = sourceNumber;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.balance = balance;
        this.postingStatus = postingStatus;
        this.postingRequestId = postingRequestId;
        this.journalId = journalId;
        this.postingFailureReason = postingFailureReason;
    }

    public Instant getDate() {
        return date;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSourceNumber() {
        return sourceNumber;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getPostingStatus() {
        return postingStatus;
    }

    public UUID getPostingRequestId() {
        return postingRequestId;
    }

    public UUID getJournalId() {
        return journalId;
    }

    public String getPostingFailureReason() {
        return postingFailureReason;
    }
}
