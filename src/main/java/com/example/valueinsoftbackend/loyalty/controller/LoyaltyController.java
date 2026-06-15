package com.example.valueinsoftbackend.loyalty.controller;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyAccountResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyEstimateRequest;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyEstimateResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyLedgerItem;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRedemptionRequest;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRedemptionResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRewardResponse;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyProgramConfig;
import com.example.valueinsoftbackend.loyalty.service.LoyaltyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/loyalty/{companyId}")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final AuthorizationService authorizationService;

    public LoyaltyController(LoyaltyService loyaltyService,
                             AuthorizationService authorizationService) {
        this.loyaltyService = loyaltyService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/accounts/by-client/{clientId}")
    public LoyaltyAccountResponse accountByClient(Principal principal,
                                                  @PathVariable @Positive int companyId,
                                                  @PathVariable @Positive int clientId,
                                                  @RequestParam @Positive int branchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.read");
        return loyaltyService.getOrCreateAccount(companyId, branchId, clientId);
    }

    @GetMapping("/accounts/by-client/{clientId}/ledger")
    public List<LoyaltyLedgerItem> ledgerByClient(Principal principal,
                                                  @PathVariable @Positive int companyId,
                                                  @PathVariable @Positive int clientId,
                                                  @RequestParam @Positive int branchId,
                                                  @RequestParam(defaultValue = "50") int limit) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.read");
        return loyaltyService.ledgerForClient(companyId, branchId, clientId, limit);
    }

    @PostMapping("/checkout/estimate")
    public LoyaltyEstimateResponse estimate(Principal principal,
                                            @PathVariable @Positive int companyId,
                                            @Valid @RequestBody LoyaltyEstimateRequest request) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                request.branchId(),
                "pos.sale.read");
        return loyaltyService.estimate(companyId, request);
    }

    @PostMapping("/rewards/eligible")
    public List<LoyaltyRewardResponse> eligibleRewards(Principal principal,
                                                       @PathVariable @Positive int companyId,
                                                       @Valid @RequestBody LoyaltyRedemptionRequest request) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                request.branchId(),
                "pos.sale.read");
        return loyaltyService.eligibleRewards(companyId, request);
    }

    @PostMapping("/redemptions/reserve")
    public LoyaltyRedemptionResponse reserveRedemption(Principal principal,
                                                       @PathVariable @Positive int companyId,
                                                       @Valid @RequestBody LoyaltyRedemptionRequest request) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                request.branchId(),
                "pos.sale.create");
        return loyaltyService.reserveRedemption(companyId, request, principal.getName());
    }

    @PostMapping("/redemptions/{redemptionId}/release")
    public LoyaltyRedemptionResponse releaseRedemption(Principal principal,
                                                       @PathVariable @Positive int companyId,
                                                       @PathVariable @Positive long redemptionId,
                                                       @RequestParam @Positive int branchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "pos.sale.create");
        return loyaltyService.releaseRedemption(companyId, redemptionId, principal.getName());
    }

    @GetMapping("/config")
    public LoyaltyProgramConfig getEffectiveConfig(Principal principal,
                                                  @PathVariable @Positive int companyId,
                                                  @RequestParam @Positive int branchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "company.settings.read");
        return loyaltyService.getEffectiveConfig(companyId, branchId);
    }

    @PutMapping("/config")
    public void updateConfig(Principal principal,
                            @PathVariable @Positive int companyId,
                            @RequestParam @Positive int branchId,
                            @Valid @RequestBody LoyaltyProgramConfig config) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "company.settings.edit");
        loyaltyService.updateConfig(companyId, config);
    }

    @GetMapping("/rewards/all")
    public List<LoyaltyRewardResponse> listAllRewards(Principal principal,
                                                      @PathVariable @Positive int companyId,
                                                      @RequestParam @Positive int branchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "company.settings.read");
        return loyaltyService.listAllRewards(companyId, branchId);
    }

    @PostMapping("/rewards")
    public void createReward(Principal principal,
                             @PathVariable @Positive int companyId,
                             @RequestParam @Positive int branchId,
                             @Valid @RequestBody LoyaltyRewardResponse reward) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "company.settings.edit");
        loyaltyService.createReward(companyId, reward, branchId);
    }

    @PutMapping("/rewards/{rewardId}")
    public void updateReward(Principal principal,
                             @PathVariable @Positive int companyId,
                             @RequestParam @Positive int branchId,
                             @PathVariable @Positive long rewardId,
                             @Valid @RequestBody LoyaltyRewardResponse reward) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "company.settings.edit");
        loyaltyService.updateReward(companyId, reward);
    }

    @DeleteMapping("/rewards/{rewardId}")
    public void deleteReward(Principal principal,
                             @PathVariable @Positive int companyId,
                             @RequestParam @Positive int branchId,
                             @PathVariable @Positive long rewardId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "company.settings.edit");
        loyaltyService.deleteReward(companyId, rewardId);
    }
}
