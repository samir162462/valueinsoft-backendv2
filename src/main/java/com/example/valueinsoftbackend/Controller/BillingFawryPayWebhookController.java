package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Billing.BillingProviderWebhookSettlementResponse;
import com.example.valueinsoftbackend.Service.billing.BillingFawryPayWebhookSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/billing/fawrypay")
public class BillingFawryPayWebhookController {

    private final BillingFawryPayWebhookSettlementService settlementService;

    public BillingFawryPayWebhookController(BillingFawryPayWebhookSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/webhook")
    public ResponseEntity<BillingProviderWebhookSettlementResponse> settleFawryPayGetWebhook(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(settlementService.settleCallback(params));
    }

    @PostMapping("/webhook")
    public ResponseEntity<BillingProviderWebhookSettlementResponse> settleFawryPayPostWebhook(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Map<String, String> params) {
        return ResponseEntity.ok(settlementService.settleCallback(mergePayload(body, params)));
    }

    private Map<String, Object> mergePayload(Map<String, Object> body, Map<String, String> params) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (body != null) {
            merged.putAll(body);
        }
        if (params != null) {
            merged.putAll(params);
        }
        return merged;
    }
}
