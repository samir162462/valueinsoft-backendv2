package com.example.valueinsoftbackend.ai.controller;

import com.example.valueinsoftbackend.ai.audit.AiAdminMonitoringService;
import com.example.valueinsoftbackend.ai.dto.AiAdminErrorsResponse;
import com.example.valueinsoftbackend.ai.dto.AiAdminToolAuditResponse;
import com.example.valueinsoftbackend.ai.dto.AiAdminTopQuestionsResponse;
import com.example.valueinsoftbackend.ai.dto.AiAdminUsageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/ai")
public class AiAdminMonitoringController {

    private final AiAdminMonitoringService monitoringService;

    public AiAdminMonitoringController(AiAdminMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/usage")
    public AiAdminUsageResponse getUsage(Principal principal,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                         @RequestParam(required = false) Integer limit) {
        return monitoringService.getUsage(principal, fromDate, toDate, limit);
    }

    @GetMapping("/tool-audit")
    public AiAdminToolAuditResponse getToolAudit(Principal principal,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                 @RequestParam(required = false) Integer limit) {
        return monitoringService.getToolAudit(principal, fromDate, toDate, limit);
    }

    @GetMapping("/errors")
    public AiAdminErrorsResponse getErrors(Principal principal,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                           @RequestParam(required = false) Integer limit) {
        return monitoringService.getErrors(principal, fromDate, toDate, limit);
    }

    @GetMapping("/top-questions")
    public AiAdminTopQuestionsResponse getTopQuestions(Principal principal,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                       @RequestParam(required = false) Integer limit) {
        return monitoringService.getTopQuestions(principal, fromDate, toDate, limit);
    }
}
