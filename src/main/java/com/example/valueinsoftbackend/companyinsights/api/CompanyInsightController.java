package com.example.valueinsoftbackend.companyinsights.api;

import com.example.valueinsoftbackend.companyinsights.api.dto.CompanyInsightSummaryDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.InsightDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.InsightPageDto;
import com.example.valueinsoftbackend.companyinsights.api.dto.InsightStatusRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/company-insights")
public class CompanyInsightController {

    private final CompanyInsightQueryService queryService;

    public CompanyInsightController(CompanyInsightQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<InsightPageDto> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String insightType,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int pageSize,
            Principal principal) {
        return ResponseEntity.ok(queryService.list(principal, severity, category, insightType,
                branchId, status, role, from, to, page, pageSize));
    }

    @GetMapping("/summary")
    public ResponseEntity<CompanyInsightSummaryDto> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {
        return ResponseEntity.ok(queryService.summary(principal, from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InsightDto> detail(@PathVariable long id, Principal principal) {
        return ResponseEntity.ok(queryService.detail(principal, id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<InsightDto> updateStatus(@PathVariable long id,
                                                   @RequestBody InsightStatusRequest request,
                                                   Principal principal) {
        return ResponseEntity.ok(queryService.updateStatus(principal, id, request));
    }

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            Principal principal) {
        int persisted = queryService.recalculate(principal, asOf);
        return ResponseEntity.ok(Map.of("accepted", true, "persisted", persisted));
    }

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {
        long backfillId = queryService.startBackfill(principal, from, to);
        return ResponseEntity.ok(Map.of("backfillId", backfillId));
    }

    @GetMapping("/backfill/{id}")
    public ResponseEntity<Map<String, Object>> backfillStatus(@PathVariable long id, Principal principal) {
        return ResponseEntity.ok(queryService.backfillStatus(principal, id));
    }
}
