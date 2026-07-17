package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceObligationsReport;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceObligationsReportModels;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class FinanceObligationsReportService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final DbFinanceObligationsReport repository;
    private final AuthorizationService authorizationService;

    public FinanceObligationsReportService(DbFinanceObligationsReport repository,
                                           AuthorizationService authorizationService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
    }

    public FinanceObligationsReportModels.Page page(String authenticatedName, int companyId, int branchId,
                                                     String side, LocalDate asOfDate, String search,
                                                     Integer limit, Integer offset) {
        authorize(authenticatedName, companyId, branchId);
        String normalizedSide = side(side);
        String normalizedSearch = search == null ? null : search.trim();
        if (normalizedSearch != null && normalizedSearch.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OBLIGATIONS_SEARCH_TOO_LONG",
                    "Search is limited to 100 characters");
        }
        int pageSize = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int pageOffset = offset == null ? 0 : offset;
        boolean includePayroll = authorizationService.hasAuthenticatedCapability(
                authenticatedName, companyId, branchId, "payroll.payment.read");
        return repository.page(companyId, branchId, normalizedSide,
                asOfDate == null ? LocalDate.now() : asOfDate, normalizedSearch, pageSize, pageOffset,
                includePayroll);
    }

    public FinanceObligationsReportModels.PartyDetails details(String authenticatedName, int companyId,
                                                                int branchId, String side, int partyId,
                                                                String partyType, String currencyCode,
                                                                LocalDate asOfDate) {
        authorize(authenticatedName, companyId, branchId);
        String normalizedSide = side(side);
        String normalizedPartyType = partyType == null ? "" : partyType.trim().toUpperCase(Locale.ROOT);
        boolean allowedPartyType = "RECEIVABLE".equals(normalizedSide)
                ? java.util.Set.of("CLIENT", "SUPPLIER").contains(normalizedPartyType)
                : java.util.Set.of("SUPPLIER", "CLIENT", "EMPLOYEE").contains(normalizedPartyType);
        if (!allowedPartyType) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OBLIGATIONS_PARTY_TYPE_INVALID",
                    "partyType is not valid for the selected side");
        }
        boolean includePayroll = authorizationService.hasAuthenticatedCapability(
                authenticatedName, companyId, branchId, "payroll.payment.read");
        if ("EMPLOYEE".equals(normalizedPartyType) && !includePayroll) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYROLL_OBLIGATIONS_ACCESS_DENIED",
                    "Payroll payment access is required to view employee obligations");
        }
        // Existing companies may use the legacy two-letter `LE` currency label.
        // Preserve it as its own displayed/filterable currency rather than guessing
        // a conversion or combining it with a different code.
        if (currencyCode == null || !currencyCode.trim().matches("[A-Za-z]{2,5}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OBLIGATIONS_CURRENCY_INVALID",
                    "A valid currencyCode is required");
        }
        FinanceObligationsReportModels.PartyDetails result = repository.details(companyId, branchId,
                normalizedSide, partyId, normalizedPartyType,
                currencyCode.trim().toUpperCase(Locale.ROOT),
                asOfDate == null ? LocalDate.now() : asOfDate, includePayroll);
        if (result == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "OBLIGATION_PARTY_NOT_FOUND",
                    "No open obligations were found for this party and currency");
        }
        return result;
    }

    private void authorize(String authenticatedName, int companyId, int branchId) {
        authorizationService.assertAuthenticatedCapability(authenticatedName, companyId, branchId,
                "finance.report.read");
    }

    private static String side(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"RECEIVABLE".equals(normalized) && !"PAYABLE".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OBLIGATIONS_SIDE_INVALID",
                    "side must be RECEIVABLE or PAYABLE");
        }
        return normalized;
    }
}
