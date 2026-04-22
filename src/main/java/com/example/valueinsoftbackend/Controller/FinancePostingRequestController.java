package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestProcessResponse;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import com.example.valueinsoftbackend.Service.FinancePostingRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/posting-requests")
public class FinancePostingRequestController {

    private final FinancePostingRequestService financePostingRequestService;

    public FinancePostingRequestController(FinancePostingRequestService financePostingRequestService) {
        this.financePostingRequestService = financePostingRequestService;
    }

    @PostMapping
    public FinancePostingRequestItem createPostingRequest(Principal principal,
                                                          @Valid @RequestBody FinancePostingRequestCreateRequest request) {
        return financePostingRequestService.createPostingRequestForAuthenticatedUser(
                principal.getName(),
                request);
    }

    @GetMapping
    public ArrayList<FinancePostingRequestItem> getPostingRequests(Principal principal,
                                                                   @RequestParam("companyId") @Positive int companyId,
                                                                   @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                                   @RequestParam(value = "status", required = false) String status,
                                                                   @RequestParam(value = "sourceModule", required = false) String sourceModule,
                                                                   @RequestParam(value = "fiscalPeriodId", required = false) UUID fiscalPeriodId,
                                                                   @RequestParam(value = "limit", required = false) @Positive Integer limit,
                                                                   @RequestParam(value = "offset", required = false) @Min(0) Integer offset) {
        return financePostingRequestService.getPostingRequestsForAuthenticatedUser(
                principal.getName(),
                companyId,
                branchId,
                status,
                sourceModule,
                fiscalPeriodId,
                limit,
                offset);
    }

    @GetMapping("/{postingRequestId}")
    public FinancePostingRequestItem getPostingRequest(Principal principal,
                                                       @PathVariable("postingRequestId") UUID postingRequestId,
                                                       @RequestParam("companyId") @Positive int companyId) {
        return financePostingRequestService.getPostingRequestForAuthenticatedUser(
                principal.getName(),
                companyId,
                postingRequestId);
    }

    @PostMapping("/{postingRequestId}/cancel")
    public FinancePostingRequestItem cancelPostingRequest(Principal principal,
                                                          @PathVariable("postingRequestId") UUID postingRequestId,
                                                          @RequestParam("companyId") @Positive int companyId) {
        return financePostingRequestService.cancelPostingRequestForAuthenticatedUser(
                principal.getName(),
                companyId,
                postingRequestId);
    }

    @PostMapping("/{postingRequestId}/retry")
    public FinancePostingRequestItem retryPostingRequest(Principal principal,
                                                         @PathVariable("postingRequestId") UUID postingRequestId,
                                                         @RequestParam("companyId") @Positive int companyId) {
        return financePostingRequestService.retryPostingRequestForAuthenticatedUser(
                principal.getName(),
                companyId,
                postingRequestId);
    }

    @PostMapping("/{postingRequestId}/process")
    public FinancePostingRequestProcessResponse processPostingRequest(Principal principal,
                                                                      @PathVariable("postingRequestId") UUID postingRequestId,
                                                                      @RequestParam("companyId") @Positive int companyId) {
        return financePostingRequestService.processPostingRequestForAuthenticatedUser(
                principal.getName(),
                companyId,
                postingRequestId);
    }

    @PostMapping("/process-next")
    public FinancePostingRequestProcessResponse processNextPostingRequest(Principal principal,
                                                                         @RequestParam("companyId") @Positive int companyId,
                                                                         @RequestParam(value = "sourceModule", required = false)
                                                                         String sourceModule) {
        return financePostingRequestService.processNextPostingRequestForAuthenticatedUser(
                principal.getName(),
                companyId,
                sourceModule);
    }
}
