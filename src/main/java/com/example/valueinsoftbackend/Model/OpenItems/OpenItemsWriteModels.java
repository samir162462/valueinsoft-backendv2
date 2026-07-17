package com.example.valueinsoftbackend.Model.OpenItems;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public final class OpenItemsWriteModels {

    private OpenItemsWriteModels() {
    }

    public record AllocationTarget(@Positive long openItemId,
                                   @NotNull @DecimalMin("0.0001") BigDecimal amount) {
    }

    public record AllocationCommand(
            @NotBlank String currencyCode,
            String idempotencyKey,
            @Valid List<AllocationTarget> allocations) {
    }

    public record AllocationLine(long allocationId, long openItemId, BigDecimal amount, String status) {
    }

    public record AllocationResult(long sourceId, BigDecimal allocatedAmount,
                                   BigDecimal sourceUnallocatedAmount, List<AllocationLine> allocations,
                                   boolean idempotencyReplay) {
    }

    public record AllocationRow(long allocationId, Integer receiptId, Long noteId, long openItemId,
                                int branchId, int partyId, String currencyCode, BigDecimal amount,
                                String status, Long reversalOfAllocationId, String idempotencyKey) {
    }

    public record NoteLock(long noteId, int branchId, int partyId, String currencyCode,
                           BigDecimal totalAmount, BigDecimal appliedAmount, BigDecimal unappliedAmount,
                           String status) {
    }

    /** Stage 7.1: update a client's credit terms (capability clients.credit.manage). */
    public record CreditUpdateCommand(
            @NotNull @DecimalMin("0") BigDecimal creditLimit,
            @jakarta.validation.constraints.Min(0) int creditTermsDays,
            @NotBlank String creditStatus,     // NORMAL | HOLD | BLOCKED (DB CHECK enforces)
            String creditNotes) {
    }

    /**
     * Row lock on the tenant "Client" for credit control (Stage 5.1). The FOR UPDATE
     * lock serializes concurrent credit sales against one client's limit for the
     * duration of the order transaction.
     */
    public record ClientCreditLock(int clientId, BigDecimal creditLimit, int creditTermsDays,
                                   String creditStatus) {
    }

    /**
     * Result of a credit-control check. {@code mode} is the branch setting value
     * (OFF/WARN/BLOCK); {@code allowed=false} means the caller must reject the sale;
     * {@code warning=true} means the sale proceeds but the breach should be shown
     * at the point of sale (WARN mode).
     */
    public record CreditCheckResult(boolean allowed, boolean warning, String mode,
                                    BigDecimal exposure, BigDecimal creditLimit,
                                    BigDecimal newExposure, String reasonCode, String message) {
    }

    public record NoteCreateCommand(
            @Positive int branchId,
            @Positive int partyId,
            @NotBlank String reason,
            String referenceType,
            Long referenceId,
            @NotBlank String currencyCode,
            @NotNull @DecimalMin("0.0001") BigDecimal totalAmount,
            String idempotencyKey,
            String notes) {
    }

    public record NoteResult(long noteId, int branchId, int partyId, String currencyCode,
                             BigDecimal totalAmount, BigDecimal appliedAmount, BigDecimal unappliedAmount,
                             String status, boolean idempotencyReplay) {
    }
}
