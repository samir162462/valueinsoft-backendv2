package com.example.valueinsoftbackend.ai.controller;

import com.example.valueinsoftbackend.ai.dto.AiDailyInsightsRequest;
import com.example.valueinsoftbackend.ai.dto.AiDailyInsightsResponse;
import com.example.valueinsoftbackend.ai.service.AiDailyInsightsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/ai")
public class AiDailyInsightsController {

    private final AiDailyInsightsService dailyInsightsService;

    public AiDailyInsightsController(AiDailyInsightsService dailyInsightsService) {
        this.dailyInsightsService = dailyInsightsService;
    }

    @PostMapping("/daily-insights")
    public ResponseEntity<AiDailyInsightsResponse> dailyInsights(@Valid @RequestBody AiDailyInsightsRequest request,
                                                                 Principal principal) {
        return ResponseEntity.ok(dailyInsightsService.generate(request, principal));
    }
}
