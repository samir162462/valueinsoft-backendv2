package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReadModels;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArOpenItemService {

    private final DbArOpenItem repository;
    private final com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService financePosting;

    public ArOpenItemService(DbArOpenItem repository,
                             com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService financePosting) {
        this.repository = repository;
        this.financePosting = financePosting;
    }

    public String companyCurrency(int companyId) {
        return repository.getCompanyCurrency(companyId);
    }

    @Transactional
    public long createPosOrderOpenItem(int companyId, int branchId, int clientId, long orderId,
                                       BigDecimal total, java.time.LocalDateTime orderTime,
                                       String orderIdempotencyKey, String actor) {
        int terms = repository.getClientCreditTermsDays(companyId, clientId);
        return repository.createOpenItem(companyId, branchId, clientId, "POS_ORDER", orderId,
                "POS-" + orderId, orderTime, orderTime.plusDays(terms), companyCurrency(companyId), total,
                "pos-order:" + normalizedKey(orderIdempotencyKey) + ":" + orderId, actor);
    }

    @Transactional
    public OpenItemsWriteModels.AllocationResult allocateReceipt(int companyId, int branchId, int clientId,
                                                                  int receiptId,
                                                                  OpenItemsWriteModels.AllocationCommand command,
                                                                  String actor) {
        OpenItemsReadModels.ReceiptLock receipt = repository.findReceiptForUpdate(companyId, receiptId);
        requirePostedSource(receipt.status(), receipt.branchId(), receipt.partyId(), branchId, clientId);
        BigDecimal available = receipt.amount().subtract(repository.sumActiveAllocationsForReceipt(companyId, receiptId));
        return allocate(companyId, branchId, clientId, receiptId, null, receiptId, command,
                available, command.currencyCode(), actor, null);
    }

    @Transactional
    public OpenItemsWriteModels.AllocationResult applyCreditNote(int companyId, int branchId, int clientId,
                                                                 long creditNoteId,
                                                                 OpenItemsWriteModels.AllocationCommand command,
                                                                 String actor) {
        OpenItemsWriteModels.NoteLock note = repository.findCreditNoteForUpdate(companyId, creditNoteId);
        requirePostedSource(note.status(), note.branchId(), note.partyId(), branchId, clientId);
        if (!note.currencyCode().equalsIgnoreCase(command.currencyCode())) {
            fail("OPEN_ITEMS_CURRENCY_MISMATCH", "Credit note currency does not match the allocation currency");
        }
        return allocate(companyId, branchId, clientId, null, creditNoteId, creditNoteId, command,
                note.unappliedAmount(), note.currencyCode(), actor, note);
    }

    private OpenItemsWriteModels.AllocationResult allocate(int companyId, int branchId, int clientId,
                                                            Integer receiptId, Long noteId, long sourceId,
                                                            OpenItemsWriteModels.AllocationCommand command,
                                                            BigDecimal available, String currencyCode, String actor,
                                                            OpenItemsWriteModels.NoteLock note) {
        String sourceKind = receiptId == null ? "note" : "receipt";
        String prefix = "ar:" + hash(companyId + ":" + sourceKind + ":" + sourceId + ":"
                + normalizedKey(command.idempotencyKey())) + ":";
        List<OpenItemsWriteModels.AllocationRow> replay = repository.findAllocationsByPrefix(companyId, prefix);
        if (!replay.isEmpty()) {
            BigDecimal replayAmount = replay.stream().map(OpenItemsWriteModels.AllocationRow::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return result(sourceId, replayAmount, available, replay, true);
        }
        if (available.signum() <= 0) {
            fail("OPEN_ITEMS_SOURCE_EXHAUSTED", "Receipt or credit note has no unallocated amount");
        }

        List<OpenItemsReadModels.OpenItem> locked;
        Map<Long, BigDecimal> requested = requested(command.allocations());
        if (requested.isEmpty()) {
            locked = repository.findSettleableForUpdate(companyId, branchId, clientId, currencyCode);
        } else {
            locked = repository.findOpenItemsForUpdate(companyId, requested.keySet().stream().sorted().toList());
        }
        List<Target> targets = buildTargets(locked, requested, available, companyId, branchId, clientId, currencyCode);
        if (targets.isEmpty()) {
            if (requested.isEmpty()) {
                return new OpenItemsWriteModels.AllocationResult(sourceId, BigDecimal.ZERO, available, List.of(), false);
            }
            fail("OPEN_ITEMS_NO_SETTLEABLE_ITEMS", "No settleable open items were found");
        }

        ArrayList<OpenItemsWriteModels.AllocationRow> inserted = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Target target : targets) {
            // Legacy tenants may contain equivalent currency codes with different casing
            // (for example LE and le). The database guard compares the allocation with the
            // locked open item exactly, so persist the item's canonical stored value.
            String allocationCurrency = target.item.currencyCode();
            long allocationId = repository.insertAllocation(companyId, branchId, clientId, receiptId, noteId,
                    target.item.openItemId(), target.amount, allocationCurrency,
                    prefix + target.item.openItemId(), actor);
            BigDecimal settled = target.item.settledAmount().add(target.amount);
            BigDecimal remaining = target.item.remainingAmount().subtract(target.amount);
            repository.updateSettlement(companyId, target.item.openItemId(), settled, remaining,
                    remaining.signum() == 0 ? "SETTLED" : "PARTIALLY_SETTLED", actor);
            inserted.add(new OpenItemsWriteModels.AllocationRow(allocationId, receiptId, noteId,
                    target.item.openItemId(), branchId, clientId, allocationCurrency, target.amount,
                    "POSTED", null, prefix + target.item.openItemId()));
            allocated = allocated.add(target.amount);
        }
        BigDecimal unallocated = available.subtract(allocated);
        if (note != null) {
            BigDecimal applied = note.appliedAmount().add(allocated);
            repository.updateCreditNoteApplication(companyId, note.noteId(), applied, unallocated,
                    unallocated.signum() == 0 ? "APPLIED" : "PARTIALLY_APPLIED", actor);
        }
        return result(sourceId, allocated, unallocated, inserted, false);
    }

    @Transactional
    public OpenItemsWriteModels.AllocationLine reverseAllocation(int companyId, long allocationId,
                                                                  String idempotencyKey, String actor) {
        OpenItemsWriteModels.AllocationRow original = repository.findAllocationForUpdate(companyId, allocationId);
        if (!"POSTED".equals(original.status()) || original.reversalOfAllocationId() != null) {
            fail("OPEN_ITEMS_ALLOCATION_NOT_REVERSIBLE", "Only an active posted allocation can be reversed");
        }
        OpenItemsReadModels.OpenItem item = repository.findOpenItemsForUpdate(companyId,
                List.of(original.openItemId())).stream().findFirst().orElseThrow();
        OpenItemsWriteModels.NoteLock note = original.noteId() == null ? null
                : repository.findCreditNoteForUpdate(companyId, original.noteId());
        long reversalId = repository.insertAllocationReversal(companyId, original,
                "ar-reversal:" + hash(allocationId + ":" + normalizedKey(idempotencyKey)), actor);
        repository.markAllocationReversed(companyId, allocationId);
        BigDecimal settled = item.settledAmount().subtract(original.amount());
        BigDecimal remaining = item.remainingAmount().add(original.amount());
        repository.updateSettlement(companyId, item.openItemId(), settled, remaining,
                settled.signum() == 0 ? "OPEN" : "PARTIALLY_SETTLED", actor);
        if (note != null) {
            BigDecimal applied = note.appliedAmount().subtract(original.amount());
            BigDecimal unapplied = note.unappliedAmount().add(original.amount());
            repository.updateCreditNoteApplication(companyId, note.noteId(), applied, unapplied,
                    applied.signum() == 0 ? "OPEN" : "PARTIALLY_APPLIED", actor);
        }
        return new OpenItemsWriteModels.AllocationLine(reversalId, original.openItemId(), original.amount(), "POSTED");
    }

    @Transactional
    public void reverseReceipt(int companyId, int receiptId, String reason, String actor) {
        OpenItemsReadModels.ReceiptLock receipt = repository.findReceiptForUpdate(companyId, receiptId);
        if (!"POSTED".equals(receipt.status())) fail("OPEN_ITEMS_RECEIPT_NOT_REVERSIBLE", "Receipt is not posted");
        if (repository.countActiveAllocationsForReceipt(companyId, receiptId) != 0) {
            fail("OPEN_ITEMS_REVERSE_ALLOCATIONS_FIRST", "Reverse all active allocations before reversing the receipt");
        }
        repository.markReceiptReversed(companyId, receiptId);
        // GL reversal (Stage 4.2): the receipt posted as customer_receipt or customer_payout —
        // both share sourceId "client-receipt-<crId>". Try receipt first; a null result means
        // no posting request exists under that type. If neither exists, the receipt predates
        // finance wiring and the subledger-only reversal is complete (see
        // FinanceOperationalPostingService.enqueueSubledgerGlReversal for the safety rules).
        String reversalSourceId = "client-receipt-reversal-" + receiptId;
        var posting = financePosting.enqueueSubledgerGlReversal(companyId, receipt.branchId(),
                "payment", "customer_receipt", "client-receipt-" + receiptId,
                reversalSourceId, reason, actor);
        if (posting == null) {
            financePosting.enqueueSubledgerGlReversal(companyId, receipt.branchId(),
                    "payment", "customer_payout", "client-receipt-" + receiptId,
                    reversalSourceId, reason, actor);
        }
    }

    @Transactional
    public void reverseOpenItem(int companyId, long openItemId, String actor) {
        OpenItemsReadModels.OpenItem item = repository.findOpenItemsForUpdate(companyId, List.of(openItemId))
                .stream().findFirst().orElseThrow();
        if (item.settledAmount().signum() != 0) {
            fail("OPEN_ITEMS_REVERSE_ALLOCATIONS_FIRST", "Reverse allocations before reversing the open item");
        }
        repository.reverseOpenItem(companyId, openItemId, actor);
    }

    private List<Target> buildTargets(List<OpenItemsReadModels.OpenItem> items, Map<Long, BigDecimal> requested,
                                      BigDecimal available, int companyId, int branchId, int partyId, String currency) {
        ArrayList<Target> result = new ArrayList<>();
        BigDecimal left = available;
        List<OpenItemsReadModels.OpenItem> ordered = requested.isEmpty()
                ? items
                : items.stream().sorted(Comparator.comparingLong(OpenItemsReadModels.OpenItem::openItemId)).toList();
        for (OpenItemsReadModels.OpenItem item : ordered) {
            validateItem(item, companyId, branchId, partyId, currency);
            BigDecimal amount = requested.isEmpty() ? left.min(item.remainingAmount()) : requested.get(item.openItemId());
            if (amount == null || amount.signum() <= 0) continue;
            if (amount.compareTo(item.remainingAmount()) > 0 || amount.compareTo(left) > 0) {
                fail("OPEN_ITEMS_OVER_ALLOCATION", "Allocation exceeds the source or open-item remaining amount");
            }
            result.add(new Target(item, amount));
            left = left.subtract(amount);
            if (left.signum() == 0) break;
        }
        if (!requested.isEmpty() && result.size() != requested.size()) {
            fail("OPEN_ITEMS_TARGET_NOT_FOUND", "One or more allocation targets are unavailable");
        }
        return result;
    }

    private void validateItem(OpenItemsReadModels.OpenItem item, int companyId, int branchId, int partyId, String currency) {
        if (item.companyId() != companyId || item.branchId() != branchId || item.partyId() != partyId
                || !item.currencyCode().equalsIgnoreCase(currency)
                || !("OPEN".equals(item.status()) || "PARTIALLY_SETTLED".equals(item.status()))) {
            fail("OPEN_ITEMS_TARGET_MISMATCH", "Open item does not match the requested party, branch, currency, or status");
        }
    }

    private static Map<Long, BigDecimal> requested(List<OpenItemsWriteModels.AllocationTarget> targets) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if (targets == null) return result;
        for (OpenItemsWriteModels.AllocationTarget target : targets) {
            if (result.putIfAbsent(target.openItemId(), target.amount()) != null) {
                fail("OPEN_ITEMS_DUPLICATE_TARGET", "An open item may appear only once per allocation command");
            }
        }
        return result;
    }

    private static OpenItemsWriteModels.AllocationResult result(long sourceId, BigDecimal amount,
                                                                 BigDecimal available,
                                                                 List<OpenItemsWriteModels.AllocationRow> rows,
                                                                 boolean replay) {
        List<OpenItemsWriteModels.AllocationLine> lines = rows.stream()
                .map(row -> new OpenItemsWriteModels.AllocationLine(
                        row.allocationId(), row.openItemId(), row.amount(), row.status())).toList();
        return new OpenItemsWriteModels.AllocationResult(sourceId, amount, available, lines, replay);
    }

    private static void requirePostedSource(String status, int sourceBranch, int sourceParty,
                                            int branchId, int partyId) {
        if (!"POSTED".equals(status) && !"OPEN".equals(status) && !"PARTIALLY_APPLIED".equals(status)) {
            fail("OPEN_ITEMS_SOURCE_NOT_POSTED", "Allocation source is not available");
        }
        if (sourceBranch != branchId || sourceParty != partyId) {
            fail("OPEN_ITEMS_SOURCE_MISMATCH", "Allocation source belongs to another branch or party");
        }
    }

    private static String normalizedKey(String key) {
        return key == null || key.isBlank() ? "auto" : key.trim();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void fail(String code, String message) {
        throw new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record Target(OpenItemsReadModels.OpenItem item, BigDecimal amount) {
    }
}
