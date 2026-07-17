package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;

@Service
public class ArCreditNoteService {

    private final DbArOpenItem repository;
    private final ArOpenItemService allocationService;
    private final FinanceOperationalPostingService financePosting;

    public ArCreditNoteService(DbArOpenItem repository, ArOpenItemService allocationService,
                               FinanceOperationalPostingService financePosting) {
        this.repository = repository;
        this.allocationService = allocationService;
        this.financePosting = financePosting;
    }

    public String companyCurrency(int companyId) {
        return allocationService.companyCurrency(companyId);
    }

    @Transactional
    public OpenItemsWriteModels.NoteResult create(int companyId, OpenItemsWriteModels.NoteCreateCommand command,
                                                   String actor) {
        String key = requireKey(command.idempotencyKey());
        OpenItemsWriteModels.NoteResult replay = repository.findCreditNoteByIdempotency(companyId, key);
        if (replay != null) return replay;
        OpenItemsWriteModels.NoteCreateCommand normalized = new OpenItemsWriteModels.NoteCreateCommand(
                command.branchId(), command.partyId(), command.reason(), command.referenceType(), command.referenceId(),
                command.currencyCode().trim().toUpperCase(), command.totalAmount(), key, command.notes());
        long id = repository.createCreditNote(companyId, normalized, actor);
        var posting = financePosting.enqueueArCreditNote(companyId, command.branchId(), command.partyId(), id,
                command.totalAmount(), normalized.currencyCode(), actor);
        repository.setCreditNotePostingReferences(companyId, id, posting.getPostingRequestId(), posting.getJournalEntryId());
        return new OpenItemsWriteModels.NoteResult(id, command.branchId(), command.partyId(),
                normalized.currencyCode(), command.totalAmount(), java.math.BigDecimal.ZERO,
                command.totalAmount(), "OPEN", false);
    }

    public OpenItemsWriteModels.AllocationResult apply(int companyId, int branchId, int clientId, long noteId,
                                                        OpenItemsWriteModels.AllocationCommand command, String actor) {
        return allocationService.applyCreditNote(companyId, branchId, clientId, noteId, command, actor);
    }

    @Transactional
    public void reverse(int companyId, long noteId, String actor) {
        OpenItemsWriteModels.NoteLock note = repository.findCreditNoteForUpdate(companyId, noteId);
        if (note.appliedAmount().signum() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "OPEN_ITEMS_REVERSE_ALLOCATIONS_FIRST",
                    "Reverse credit-note applications before reversing the note");
        }
        repository.markCreditNoteReversed(companyId, noteId, actor);
        // GL reversal (Stage 4.7): issuance posted through pos/sale_return with
        // sourceId "ar-credit-note-<id>" (enqueueArCreditNote). The generic reversal
        // mirrors that journal and flips it to reversed; null means issuance never
        // posted (then the CONFLICT path inside the enqueue protects a pending one).
        financePosting.enqueueSubledgerGlReversal(companyId, note.branchId(),
                "pos", "sale_return", "ar-credit-note-" + noteId,
                "ar-credit-note-reversal-" + noteId, "Credit note reversed", actor);
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OPEN_ITEMS_IDEMPOTENCY_REQUIRED", "idempotencyKey is required");
        }
        return key.trim();
    }
}
