package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceObligationsReportModels;
import com.example.valueinsoftbackend.Service.finance.FinanceObligationsReportService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/finance/reports/obligations")
public class FinanceObligationsReportController {

    private final FinanceObligationsReportService service;

    public FinanceObligationsReportController(FinanceObligationsReportService service) {
        this.service = service;
    }

    @GetMapping
    public FinanceObligationsReportModels.Page page(
            Principal principal,
            @RequestParam @Positive int companyId,
            @RequestParam @Positive int branchId,
            @RequestParam String side,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @Positive Integer limit,
            @RequestParam(required = false) @Min(0) Integer offset) {
        return service.page(principal.getName(), companyId, branchId, side, asOfDate, search, limit, offset);
    }

    @GetMapping("/{side}/{partyId}")
    public FinanceObligationsReportModels.PartyDetails details(
            Principal principal,
            @PathVariable String side,
            @PathVariable @Positive int partyId,
            @RequestParam @Positive int companyId,
            @RequestParam @Positive int branchId,
            @RequestParam String partyType,
            @RequestParam String currencyCode,
            @RequestParam(required = false) LocalDate asOfDate) {
        return service.details(principal.getName(), companyId, branchId, side, partyId,
                partyType, currencyCode, asOfDate);
    }
}
