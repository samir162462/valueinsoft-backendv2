package com.example.valueinsoftbackend.customerbehavior.api;

import com.example.valueinsoftbackend.customerbehavior.ai.CustomerBehaviorAiService;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorAiRequest;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorConfig;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorFilter;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorInsight;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorOverview;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorPage;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorProfile;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorRow;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerPreferenceSummary;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerProductAffinity;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRetentionCohort;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegmentSummary;
import com.example.valueinsoftbackend.customerbehavior.service.CustomerBehaviorService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/customer-behavior")
public class CustomerBehaviorController {

    private final CustomerBehaviorService behaviorService;
    private final CustomerBehaviorAiService aiService;

    public CustomerBehaviorController(CustomerBehaviorService behaviorService,
                                      CustomerBehaviorAiService aiService) {
        this.behaviorService = behaviorService;
        this.aiService = aiService;
    }

    @PostMapping("/overview")
    public ResponseEntity<CustomerBehaviorOverview> overview(@Valid @RequestBody(required = false) CustomerBehaviorFilter filter,
                                                             Principal principal) {
        return ResponseEntity.ok(behaviorService.getOverview(filter, principal));
    }

    @PostMapping("/segments")
    public ResponseEntity<List<CustomerSegmentSummary>> segments(@Valid @RequestBody(required = false) CustomerBehaviorFilter filter,
                                                                 Principal principal) {
        return ResponseEntity.ok(behaviorService.getSegments(filter, principal));
    }

    @PostMapping("/customers/search")
    public ResponseEntity<CustomerBehaviorPage<CustomerBehaviorRow>> searchCustomers(@Valid @RequestBody(required = false) CustomerBehaviorFilter filter,
                                                                                    Principal principal) {
        return ResponseEntity.ok(behaviorService.searchCustomers(filter, principal));
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CustomerBehaviorProfile> customerProfile(@PathVariable long customerId,
                                                                   @RequestParam(required = false) List<Integer> branchIds,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                                   @RequestParam(required = false) String locale,
                                                                   Principal principal) {
        return ResponseEntity.ok(behaviorService.getCustomerProfile(customerId, branchIds, fromDate, toDate, locale, principal));
    }

    @PostMapping("/preferences")
    public ResponseEntity<CustomerPreferenceSummary> preferences(@Valid @RequestBody(required = false) CustomerBehaviorFilter filter,
                                                                 Principal principal) {
        return ResponseEntity.ok(behaviorService.getPreferences(filter, principal));
    }

    @PostMapping("/affinity")
    public ResponseEntity<List<CustomerProductAffinity>> affinity(@Valid @RequestBody(required = false) CustomerBehaviorFilter filter,
                                                                  Principal principal) {
        return ResponseEntity.ok(behaviorService.getAffinity(filter, principal));
    }

    @PostMapping("/cohorts")
    public ResponseEntity<List<CustomerRetentionCohort>> cohorts(@Valid @RequestBody(required = false) CustomerBehaviorFilter filter,
                                                                 Principal principal) {
        return ResponseEntity.ok(behaviorService.getCohorts(filter, principal));
    }

    @PostMapping("/ai-insights")
    public ResponseEntity<CustomerBehaviorInsight> aiInsights(@Valid @RequestBody(required = false) CustomerBehaviorAiRequest request,
                                                              Principal principal) {
        return ResponseEntity.ok(aiService.generate(request, principal));
    }

    @GetMapping("/config")
    public ResponseEntity<CustomerBehaviorConfig> getConfig(Principal principal) {
        return ResponseEntity.ok(behaviorService.getConfig(principal));
    }

    @PutMapping("/config")
    public ResponseEntity<CustomerBehaviorConfig> updateConfig(@Valid @RequestBody CustomerBehaviorConfig config,
                                                               Principal principal) {
        return ResponseEntity.ok(behaviorService.updateConfig(config, principal));
    }
}
