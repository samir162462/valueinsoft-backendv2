package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import com.example.valueinsoftbackend.Service.openitems.ApDebitNoteService;
import com.example.valueinsoftbackend.Service.openitems.ApOpenItemService;
import com.example.valueinsoftbackend.Service.openitems.ArCreditNoteService;
import com.example.valueinsoftbackend.Service.openitems.ArOpenItemService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
public class OpenItemsWriteController {

    private final ArOpenItemService ar;
    private final ApOpenItemService ap;
    private final ArCreditNoteService creditNotes;
    private final ApDebitNoteService debitNotes;
    private final AuthorizationService authorization;

    public OpenItemsWriteController(ArOpenItemService ar, ApOpenItemService ap,
                                    ArCreditNoteService creditNotes, ApDebitNoteService debitNotes,
                                    AuthorizationService authorization) {
        this.ar = ar;
        this.ap = ap;
        this.creditNotes = creditNotes;
        this.debitNotes = debitNotes;
        this.authorization = authorization;
    }

    @PostMapping("/clientAccount/{companyId}/{branchId}/{clientId}/receipts/{receiptId}/allocations")
    public OpenItemsWriteModels.AllocationResult allocateClientReceipt(
            @PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
            @PathVariable @Positive int clientId, @PathVariable @Positive int receiptId,
            @Valid @RequestBody OpenItemsWriteModels.AllocationCommand command, Principal principal) {
        auth(principal, companyId, branchId, "clients.openitems.allocate");
        return ar.allocateReceipt(companyId, branchId, clientId, receiptId, command, principal.getName());
    }

    @PostMapping("/clientAccount/{companyId}/{branchId}/allocations/{allocationId}/reverse")
    public OpenItemsWriteModels.AllocationLine reverseClientAllocation(
            @PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
            @PathVariable @Positive long allocationId,
            @RequestBody(required = false) Map<String, String> body, Principal principal) {
        auth(principal, companyId, branchId, "clients.openitems.allocate");
        return ar.reverseAllocation(companyId, allocationId, body == null ? null : body.get("idempotencyKey"), principal.getName());
    }

    @PostMapping("/clientAccount/{companyId}/{branchId}/receipts/{receiptId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseClientReceipt(@PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
                                     @PathVariable @Positive int receiptId,
                                     @RequestBody(required = false) Map<String, String> body, Principal principal) {
        auth(principal, companyId, branchId, "clients.openitems.allocate");
        ar.reverseReceipt(companyId, receiptId, body == null ? null : body.get("reason"), principal.getName());
    }

    @PostMapping("/clientAccount/{companyId}/{branchId}/open-items/{openItemId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseClientOpenItem(@PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
                                      @PathVariable @Positive long openItemId, Principal principal) {
        auth(principal, companyId, branchId, "clients.creditnote.reverse");
        ar.reverseOpenItem(companyId, openItemId, principal.getName());
    }

    @PostMapping("/clientAccount/{companyId}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenItemsWriteModels.NoteResult createCreditNote(
            @PathVariable @Positive int companyId,
            @Valid @RequestBody OpenItemsWriteModels.NoteCreateCommand command, Principal principal) {
        auth(principal, companyId, command.branchId(), "clients.creditnote.create");
        return creditNotes.create(companyId, command, principal.getName());
    }

    @PostMapping("/clientAccount/{companyId}/{branchId}/{clientId}/credit-notes/{noteId}/apply")
    public OpenItemsWriteModels.AllocationResult applyCreditNote(
            @PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
            @PathVariable @Positive int clientId, @PathVariable @Positive long noteId,
            @Valid @RequestBody OpenItemsWriteModels.AllocationCommand command, Principal principal) {
        auth(principal, companyId, branchId, "clients.openitems.allocate");
        return creditNotes.apply(companyId, branchId, clientId, noteId, command, principal.getName());
    }

    @PostMapping("/clientAccount/{companyId}/{branchId}/credit-notes/{noteId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseCreditNote(@PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
                                  @PathVariable @Positive long noteId, Principal principal) {
        auth(principal, companyId, branchId, "clients.creditnote.reverse");
        creditNotes.reverse(companyId, noteId, principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/{branchId}/{supplierId}/receipts/{receiptId}/allocations")
    public OpenItemsWriteModels.AllocationResult allocateSupplierReceipt(
            @PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
            @PathVariable @Positive int supplierId, @PathVariable @Positive int receiptId,
            @Valid @RequestBody OpenItemsWriteModels.AllocationCommand command, Principal principal) {
        auth(principal, companyId, branchId, "suppliers.openitems.allocate");
        return ap.allocateReceipt(companyId, branchId, supplierId, receiptId, command, principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/{branchId}/allocations/{allocationId}/reverse")
    public OpenItemsWriteModels.AllocationLine reverseSupplierAllocation(
            @PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
            @PathVariable @Positive long allocationId,
            @RequestBody(required = false) Map<String, String> body, Principal principal) {
        auth(principal, companyId, branchId, "suppliers.openitems.allocate");
        return ap.reverseAllocation(companyId, allocationId, body == null ? null : body.get("idempotencyKey"), principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/{branchId}/receipts/{receiptId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseSupplierReceipt(@PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
                                       @PathVariable @Positive int receiptId,
                                       @RequestBody(required = false) Map<String, String> body, Principal principal) {
        auth(principal, companyId, branchId, "suppliers.openitems.allocate");
        ap.reverseReceipt(companyId, receiptId, body == null ? null : body.get("reason"), principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/{branchId}/open-items/{openItemId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseSupplierOpenItem(@PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
                                        @PathVariable @Positive long openItemId, Principal principal) {
        auth(principal, companyId, branchId, "suppliers.debitnote.reverse");
        ap.reverseOpenItem(companyId, openItemId, principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/debit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenItemsWriteModels.NoteResult createDebitNote(
            @PathVariable @Positive int companyId,
            @Valid @RequestBody OpenItemsWriteModels.NoteCreateCommand command, Principal principal) {
        auth(principal, companyId, command.branchId(), "suppliers.debitnote.create");
        return debitNotes.create(companyId, command, principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/{branchId}/{supplierId}/debit-notes/{noteId}/apply")
    public OpenItemsWriteModels.AllocationResult applyDebitNote(
            @PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
            @PathVariable @Positive int supplierId, @PathVariable @Positive long noteId,
            @Valid @RequestBody OpenItemsWriteModels.AllocationCommand command, Principal principal) {
        auth(principal, companyId, branchId, "suppliers.openitems.allocate");
        return debitNotes.apply(companyId, branchId, supplierId, noteId, command, principal.getName());
    }

    @PostMapping("/suppliers/{companyId}/{branchId}/debit-notes/{noteId}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverseDebitNote(@PathVariable @Positive int companyId, @PathVariable @Positive int branchId,
                                 @PathVariable @Positive long noteId, Principal principal) {
        auth(principal, companyId, branchId, "suppliers.debitnote.reverse");
        debitNotes.reverse(companyId, noteId, principal.getName());
    }

    private void auth(Principal principal, int companyId, int branchId, String capability) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, branchId, capability);
    }
}
