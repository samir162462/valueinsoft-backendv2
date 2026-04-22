package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceJournalDetailResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalEntryItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceJournalLineItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceManualJournalUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceJournalReversalRequest;
import com.example.valueinsoftbackend.Service.FinanceJournalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/journals")
public class FinanceJournalController {

    private final FinanceJournalService financeJournalService;

    public FinanceJournalController(FinanceJournalService financeJournalService) {
        this.financeJournalService = financeJournalService;
    }

    @GetMapping
    public ArrayList<FinanceJournalEntryItem> getJournals(Principal principal,
                                                          @RequestParam("companyId") @Positive int companyId,
                                                          @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                          @RequestParam(value = "fiscalPeriodId", required = false) UUID fiscalPeriodId,
                                                          @RequestParam(value = "fromDate", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                          @RequestParam(value = "toDate", required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                          @RequestParam(value = "status", required = false) String status,
                                                          @RequestParam(value = "journalType", required = false) String journalType,
                                                          @RequestParam(value = "sourceModule", required = false) String sourceModule,
                                                          @RequestParam(value = "limit", required = false) @Positive Integer limit,
                                                          @RequestParam(value = "offset", required = false) @Min(0) Integer offset) {
        return financeJournalService.getJournalsForAuthenticatedUser(
                principal.getName(),
                companyId,
                branchId,
                fiscalPeriodId,
                fromDate,
                toDate,
                status,
                journalType,
                sourceModule,
                limit,
                offset);
    }

    @PostMapping("/manual-drafts")
    public FinanceJournalDetailResponse createManualDraftJournal(Principal principal,
                                                                 @Valid @RequestBody FinanceManualJournalCreateRequest request) {
        return financeJournalService.createManualDraftJournalForAuthenticatedUser(principal.getName(), request);
    }

    @PutMapping("/{journalEntryId}/manual-draft")
    public FinanceJournalDetailResponse updateManualDraftJournal(Principal principal,
                                                                 @PathVariable("journalEntryId") UUID journalEntryId,
                                                                 @Valid @RequestBody FinanceManualJournalUpdateRequest request) {
        return financeJournalService.updateManualDraftJournalForAuthenticatedUser(principal.getName(), journalEntryId, request);
    }

    @PostMapping("/{journalEntryId}/void-draft")
    public FinanceJournalDetailResponse voidManualDraftJournal(Principal principal,
                                                               @PathVariable("journalEntryId") UUID journalEntryId,
                                                               @RequestParam("companyId") @Positive int companyId,
                                                               @RequestParam("version") @Min(1) int version) {
        return financeJournalService.voidManualDraftJournalForAuthenticatedUser(principal.getName(), companyId, journalEntryId, version);
    }

    @PostMapping("/{journalEntryId}/validate-draft")
    public FinanceJournalDetailResponse validateManualDraftJournal(Principal principal,
                                                                   @PathVariable("journalEntryId") UUID journalEntryId,
                                                                   @RequestParam("companyId") @Positive int companyId,
                                                                   @RequestParam("version") @Min(1) int version) {
        return financeJournalService.validateManualDraftJournalForAuthenticatedUser(principal.getName(), companyId, journalEntryId, version);
    }

    @PostMapping("/{journalEntryId}/post")
    public FinanceJournalDetailResponse postValidatedManualJournal(Principal principal,
                                                                   @PathVariable("journalEntryId") UUID journalEntryId,
                                                                   @RequestParam("companyId") @Positive int companyId,
                                                                   @RequestParam("version") @Min(1) int version) {
        return financeJournalService.postValidatedManualJournalForAuthenticatedUser(principal.getName(), companyId, journalEntryId, version);
    }

    @PostMapping("/{journalEntryId}/reverse")
    public FinanceJournalDetailResponse reversePostedManualJournal(Principal principal,
                                                                   @PathVariable("journalEntryId") UUID journalEntryId,
                                                                   @Valid @RequestBody FinanceJournalReversalRequest request) {
        return financeJournalService.reversePostedManualJournalForAuthenticatedUser(principal.getName(), journalEntryId, request);
    }

    @GetMapping("/{journalEntryId}")
    public FinanceJournalDetailResponse getJournalDetail(Principal principal,
                                                         @PathVariable("journalEntryId") UUID journalEntryId,
                                                         @RequestParam("companyId") @Positive int companyId) {
        return financeJournalService.getJournalDetailForAuthenticatedUser(principal.getName(), companyId, journalEntryId);
    }

    @GetMapping("/{journalEntryId}/lines")
    public ArrayList<FinanceJournalLineItem> getJournalLines(Principal principal,
                                                             @PathVariable("journalEntryId") UUID journalEntryId,
                                                             @RequestParam("companyId") @Positive int companyId) {
        return financeJournalService.getJournalLinesForAuthenticatedUser(principal.getName(), companyId, journalEntryId);
    }
}
