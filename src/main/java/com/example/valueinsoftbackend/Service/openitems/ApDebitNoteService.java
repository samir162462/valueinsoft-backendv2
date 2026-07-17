package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbApOpenItem;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;

@Service
public class ApDebitNoteService {

    private final DbApOpenItem repository;
    private final ApOpenItemService allocationService;
    private final FinanceOperationalPostingService financePosting;

    public ApDebitNoteService(DbApOpenItem repository, ApOpenItemService allocationService,
                              FinanceOperationalPostingService financePosting) {
        this.repository = repository;
        this.allocationService = allocationService;
        this.financePosting = financePosting;
    }

    @Transactional
    public OpenItemsWriteModels.NoteResult create(int companyId, OpenItemsWriteModels.NoteCreateCommand command,
                                                   String actor) {
        String key = requireKey(command.idempotencyKey());
        OpenItemsWriteModels.NoteResult replay = repository.findDebitNoteByIdempotency(companyId, key);
        if (replay != null) return replay;
        OpenItemsWriteModels.NoteCreateCommand normalized = new OpenItemsWriteModels.NoteCreateCommand(
                command.branchId(), command.partyId(), command.reason(), command.referenceType(), command.referenceId(),
                command.currencyCode().trim().toUpperCase(), command.totalAmount(), key, command.notes());
        long id = repository.createDebitNote(companyId, normalized, actor);
        var posting = financePosting.enqueueApDebitNote(companyId, command.branchId(), command.partyId(), id,
                command.totalAmount(), normalized.currencyCode(), actor);
        repository.setDebitNotePostingReferences(companyId, id, posting.getPostingRequestId(), posting.getJournalEntryId());
        return new OpenItemsWriteModels.NoteResult(id, command.branchId(), command.partyId(),
                normalized.currencyCode(), command.totalAmount(), java.math.BigDecimal.ZERO,
                command.totalAmount(), "OPEN", false);
    }

    public OpenItemsWriteModels.AllocationResult apply(int companyId, int branchId, int supplierId, long noteId,
                                                        OpenItemsWriteModels.AllocationCommand command, String actor) {
        return allocationService.applyDebitNote(companyId, branchId, supplierId, noteId, command, actor);
    }

    @Transactional
    public void reverse(int companyId, long noteId, String actor) {
        OpenItemsWriteModels.NoteLock note = repository.findDebitNoteForUpdate(companyId, noteId);
        if (note.appliedAmount().signum() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "OPEN_ITEMS_REVERSE_ALLOCATIONS_FIRST",
                    "Reverse debit-note applications before reversing the note");
        }
        repository.markDebitNoteReversed(companyId, noteId, actor);
        // GL reversal (Stage 4.7): issuance posted through purchase/purchase_return with
        // sourceId "ap-debit-note-<id>" (enqueueApDebitNote); mirror-and-flip via the
        // generic subledger reversal. Null = never posted; pending -> CONFLICT inside.
        financePosting.enqueueSubledgerGlReversal(companyId, note.branchId(),
                "purchase", "purchase_return", "ap-debit-note-" + noteId,
                "ap-debit-note-reversal-" + noteId, "Debit note reversed", actor);
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPEN_ITEMS_IDEMPOTENCY_REQUIRED", "idempotencyKey is required");
        }
        return key.trim();
    }
}
