package com.example.valueinsoftbackend.companyinsights.api;

import com.example.valueinsoftbackend.companyinsights.api.dto.CompanyInsightSettingsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/ai/company-insights/settings")
public class CompanyInsightSettingsController {

    private final CompanyInsightQueryService queryService;

    public CompanyInsightSettingsController(CompanyInsightQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<CompanyInsightSettingsDto> getSettings(Principal principal) {
        return ResponseEntity.ok(queryService.getSettings(principal));
    }

    @PutMapping
    public ResponseEntity<CompanyInsightSettingsDto> updateSettings(@RequestBody CompanyInsightSettingsDto dto,
                                                                    Principal principal) {
        return ResponseEntity.ok(queryService.updateSettings(principal, dto));
    }
}
