package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceProjection;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountBalanceRebuildResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceProjectionService {

    private final DbFinanceProjection dbFinanceProjection;
    private final DbFinanceSetup dbFinanceSetup;
    private final AuthorizationService authorizationService;
    private final FinanceAuditService financeAuditService;

    public FinanceProjectionService(DbFinanceProjection dbFinanceProjection,
                                    DbFinanceSetup dbFinanceSetup,
                                    AuthorizationService authorizationService,
                                    FinanceAuditService financeAuditService) {
        this.dbFinanceProjection = dbFinanceProjection;
        this.dbFinanceSetup = dbFinanceSetup;
        this.authorizationService = authorizationService;
        this.financeAuditService = financeAuditService;
    }

    @Transactional
    public FinanceAccountBalanceRebuildResponse rebuildAccountBalancesForAuthenticatedUser(String authenticatedName,
                                                                                           int companyId,
                                                                                           UUID fiscalPeriodId,
                                                                                           String currencyCode) {
        requireCompany(companyId);
        requireFiscalPeriod(companyId, fiscalPeriodId);
        validateCurrencyCode(currencyCode);
        authorizeEdit(authenticatedName, companyId);

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        int resetRowCount = dbFinanceProjection.resetAccountBalancesForPeriod(
                companyId,
                fiscalPeriodId,
                currencyCode,
                actorUserId);
        int branchProjectionRowCount = dbFinanceProjection.rebuildBranchAccountBalances(
                companyId,
                fiscalPeriodId,
                currencyCode,
                actorUserId);
        int companyProjectionRowCount = dbFinanceProjection.rebuildCompanyAccountBalances(
                companyId,
                fiscalPeriodId,
                currencyCode,
                actorUserId);
        long totalProjectionRowCount = dbFinanceProjection.countAccountBalanceRows(
                companyId,
                fiscalPeriodId,
                currencyCode);
        DbFinanceProjection.ProjectionTotals totals = dbFinanceProjection.getCompanyProjectionTotals(
                companyId,
                fiscalPeriodId,
                currencyCode);

        FinanceAccountBalanceRebuildResponse response = new FinanceAccountBalanceRebuildResponse(
                companyId,
                fiscalPeriodId,
                currencyCode,
                resetRowCount,
                branchProjectionRowCount,
                companyProjectionRowCount,
                totalProjectionRowCount,
                totals.totalClosingDebit(),
                totals.totalClosingCredit(),
                totals.totalClosingDebit().compareTo(totals.totalClosingCredit()) == 0,
                Instant.now());

        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                null,
                "finance.projection.account_balance.rebuilt",
                "finance_account_balance",
                fiscalPeriodId.toString(),
                Map.of(
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "currencyCode", currencyCode,
                        "resetRowCount", resetRowCount,
                        "branchProjectionRowCount", branchProjectionRowCount,
                        "companyProjectionRowCount", companyProjectionRowCount,
                        "totalProjectionRowCount", totalProjectionRowCount,
                        "balanced", response.isBalanced()),
                "Account balance projection rebuilt");
        return response;
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private void requireFiscalPeriod(int companyId, UUID fiscalPeriodId) {
        if (fiscalPeriodId == null || !dbFinanceSetup.fiscalPeriodExists(companyId, fiscalPeriodId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }
    }

    private void validateCurrencyCode(String currencyCode) {
        if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }
    }

    private void authorizeEdit(String authenticatedName, int companyId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                null,
                "finance.entry.edit");
    }
}
