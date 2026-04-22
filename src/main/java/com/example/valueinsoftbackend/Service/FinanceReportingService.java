package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceReporting;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceBalanceSheetResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceGeneralLedgerLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceGeneralLedgerResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceProfitAndLossResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceStatementLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceLineItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceReportingService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final DbFinanceReporting dbFinanceReporting;
    private final DbFinanceSetup dbFinanceSetup;
    private final AuthorizationService authorizationService;
    private final FinanceAuditService financeAuditService;

    public FinanceReportingService(DbFinanceReporting dbFinanceReporting,
                                   DbFinanceSetup dbFinanceSetup,
                                   AuthorizationService authorizationService,
                                   FinanceAuditService financeAuditService) {
        this.dbFinanceReporting = dbFinanceReporting;
        this.dbFinanceSetup = dbFinanceSetup;
        this.authorizationService = authorizationService;
        this.financeAuditService = financeAuditService;
    }

    public FinanceTrialBalanceResponse getTrialBalanceForAuthenticatedUser(String authenticatedName,
                                                                          int companyId,
                                                                          UUID fiscalPeriodId,
                                                                          Integer branchId,
                                                                          String currencyCode,
                                                                          boolean includeZeroBalances) {
        requireCompany(companyId);
        requireFiscalPeriod(companyId, fiscalPeriodId);
        requireBranchIfPresent(companyId, branchId);
        validateCurrencyCode(currencyCode);
        authorizeRead(authenticatedName, companyId, branchId);

        ArrayList<FinanceTrialBalanceLineItem> lines = dbFinanceReporting.getTrialBalanceLines(
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                includeZeroBalances);

        TrialBalanceTotals totals = calculateTotals(lines);
        FinanceTrialBalanceResponse response = new FinanceTrialBalanceResponse(
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                includeZeroBalances,
                totals.totalOpeningDebit(),
                totals.totalOpeningCredit(),
                totals.totalPeriodDebit(),
                totals.totalPeriodCredit(),
                totals.totalClosingDebit(),
                totals.totalClosingCredit(),
                totals.totalClosingDebit().compareTo(totals.totalClosingCredit()) == 0,
                Instant.now(),
                lines);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                branchId,
                "finance.report.trial_balance.generated",
                "finance_report",
                fiscalPeriodId.toString(),
                Map.of(
                        "reportType", "trial_balance",
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "currencyCode", currencyCode,
                        "includeZeroBalances", includeZeroBalances,
                        "lineCount", lines.size(),
                        "totalClosingDebit", response.getTotalClosingDebit(),
                        "totalClosingCredit", response.getTotalClosingCredit(),
                        "balanced", response.isBalanced()),
                "Trial balance generated");
        return response;
    }

    public FinanceGeneralLedgerResponse getGeneralLedgerForAuthenticatedUser(String authenticatedName,
                                                                             int companyId,
                                                                             UUID fiscalPeriodId,
                                                                             UUID accountId,
                                                                             Integer branchId,
                                                                             String currencyCode,
                                                                             Integer limit,
                                                                             Integer offset) {
        requireCompany(companyId);
        requireFiscalPeriod(companyId, fiscalPeriodId);
        requireBranchIfPresent(companyId, branchId);
        validateCurrencyCode(currencyCode);
        authorizeRead(authenticatedName, companyId, branchId);

        if (accountId == null || !dbFinanceSetup.accountExists(companyId, accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_INVALID",
                    "Account does not belong to the company");
        }

        FinanceAccountItem account = dbFinanceSetup.getAccountById(companyId, accountId);
        DbFinanceReporting.LedgerBalanceValue balance = dbFinanceReporting.getLedgerBalance(
                companyId,
                fiscalPeriodId,
                accountId,
                branchId,
                currencyCode);

        BigDecimal openingNormalBalance = normalBalanceAmount(
                account.getNormalBalance(),
                balance.openingDebit(),
                balance.openingCredit());
        BigDecimal closingNormalBalance = normalBalanceAmount(
                account.getNormalBalance(),
                balance.closingDebit(),
                balance.closingCredit());
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);

        ArrayList<FinanceGeneralLedgerLineItem> lines = dbFinanceReporting.getGeneralLedgerLines(
                companyId,
                fiscalPeriodId,
                accountId,
                branchId,
                currencyCode,
                openingNormalBalance,
                normalizedLimit,
                normalizedOffset);

        FinanceGeneralLedgerResponse response = new FinanceGeneralLedgerResponse(
                companyId,
                fiscalPeriodId,
                accountId,
                account.getAccountCode(),
                account.getAccountName(),
                account.getNormalBalance(),
                branchId,
                currencyCode,
                balance.openingDebit(),
                balance.openingCredit(),
                balance.periodDebit(),
                balance.periodCredit(),
                balance.closingDebit(),
                balance.closingCredit(),
                openingNormalBalance,
                closingNormalBalance,
                normalizedLimit,
                normalizedOffset,
                Instant.now(),
                lines);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                branchId,
                "finance.report.general_ledger.generated",
                "finance_report",
                accountId.toString(),
                Map.of(
                        "reportType", "general_ledger",
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "accountId", accountId.toString(),
                        "accountCode", account.getAccountCode(),
                        "currencyCode", currencyCode,
                        "lineCount", lines.size(),
                        "limit", normalizedLimit,
                        "offset", normalizedOffset,
                        "closingNormalBalance", response.getClosingNormalBalance()),
                "General ledger generated");
        return response;
    }

    public FinanceProfitAndLossResponse getProfitAndLossForAuthenticatedUser(String authenticatedName,
                                                                             int companyId,
                                                                             UUID fiscalPeriodId,
                                                                             Integer branchId,
                                                                             String currencyCode,
                                                                             boolean includeZeroBalances) {
        requireCompany(companyId);
        requireFiscalPeriod(companyId, fiscalPeriodId);
        requireBranchIfPresent(companyId, branchId);
        validateCurrencyCode(currencyCode);
        authorizeRead(authenticatedName, companyId, branchId);

        ArrayList<FinanceStatementLineItem> statementLines = dbFinanceReporting.getStatementLines(
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                new ArrayList<>(List.of("revenue", "expense")),
                includeZeroBalances);
        ArrayList<FinanceStatementLineItem> revenueLines = filterLines(statementLines, "revenue");
        ArrayList<FinanceStatementLineItem> expenseLines = filterLines(statementLines, "expense");
        BigDecimal totalRevenue = sumAmounts(revenueLines);
        BigDecimal totalExpenses = sumAmounts(expenseLines);
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);

        FinanceProfitAndLossResponse response = new FinanceProfitAndLossResponse(
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                totalRevenue,
                totalExpenses,
                netIncome,
                Instant.now(),
                revenueLines,
                expenseLines);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                branchId,
                "finance.report.profit_and_loss.generated",
                "finance_report",
                fiscalPeriodId.toString(),
                Map.of(
                        "reportType", "profit_and_loss",
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "currencyCode", currencyCode,
                        "includeZeroBalances", includeZeroBalances,
                        "totalRevenue", totalRevenue,
                        "totalExpenses", totalExpenses,
                        "netIncome", netIncome),
                "Profit and loss generated");
        return response;
    }

    public FinanceBalanceSheetResponse getBalanceSheetForAuthenticatedUser(String authenticatedName,
                                                                           int companyId,
                                                                           UUID fiscalPeriodId,
                                                                           Integer branchId,
                                                                           String currencyCode,
                                                                           boolean includeZeroBalances) {
        requireCompany(companyId);
        requireFiscalPeriod(companyId, fiscalPeriodId);
        requireBranchIfPresent(companyId, branchId);
        validateCurrencyCode(currencyCode);
        authorizeRead(authenticatedName, companyId, branchId);

        ArrayList<FinanceStatementLineItem> statementLines = dbFinanceReporting.getStatementLines(
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                new ArrayList<>(List.of("asset", "liability", "equity", "revenue", "expense")),
                includeZeroBalances);
        ArrayList<FinanceStatementLineItem> assetLines = filterLines(statementLines, "asset");
        ArrayList<FinanceStatementLineItem> liabilityLines = filterLines(statementLines, "liability");
        ArrayList<FinanceStatementLineItem> equityLines = filterLines(statementLines, "equity");
        BigDecimal totalAssets = sumAmounts(assetLines);
        BigDecimal totalLiabilities = sumAmounts(liabilityLines);
        BigDecimal totalEquity = sumAmounts(equityLines);
        BigDecimal totalRevenue = sumAmounts(filterLines(statementLines, "revenue"));
        BigDecimal totalExpenses = sumAmounts(filterLines(statementLines, "expense"));
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
        BigDecimal totalEquityIncludingNetIncome = totalEquity.add(netIncome);
        BigDecimal liabilitiesAndEquity = totalLiabilities.add(totalEquityIncludingNetIncome);
        BigDecimal balanceDifference = totalAssets.subtract(liabilitiesAndEquity);

        FinanceBalanceSheetResponse response = new FinanceBalanceSheetResponse(
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                totalAssets,
                totalLiabilities,
                totalEquity,
                netIncome,
                totalEquityIncludingNetIncome,
                liabilitiesAndEquity,
                balanceDifference,
                balanceDifference.compareTo(ZERO) == 0,
                Instant.now(),
                assetLines,
                liabilityLines,
                equityLines);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                branchId,
                "finance.report.balance_sheet.generated",
                "finance_report",
                fiscalPeriodId.toString(),
                Map.of(
                        "reportType", "balance_sheet",
                        "fiscalPeriodId", fiscalPeriodId.toString(),
                        "currencyCode", currencyCode,
                        "includeZeroBalances", includeZeroBalances,
                        "totalAssets", totalAssets,
                        "liabilitiesAndEquity", liabilitiesAndEquity,
                        "balanceDifference", balanceDifference,
                        "balanced", response.isBalanced()),
                "Balance sheet generated");
        return response;
    }

    private TrialBalanceTotals calculateTotals(ArrayList<FinanceTrialBalanceLineItem> lines) {
        BigDecimal totalOpeningDebit = ZERO;
        BigDecimal totalOpeningCredit = ZERO;
        BigDecimal totalPeriodDebit = ZERO;
        BigDecimal totalPeriodCredit = ZERO;
        BigDecimal totalClosingDebit = ZERO;
        BigDecimal totalClosingCredit = ZERO;

        for (FinanceTrialBalanceLineItem line : lines) {
            totalOpeningDebit = totalOpeningDebit.add(line.getOpeningDebit());
            totalOpeningCredit = totalOpeningCredit.add(line.getOpeningCredit());
            totalPeriodDebit = totalPeriodDebit.add(line.getPeriodDebit());
            totalPeriodCredit = totalPeriodCredit.add(line.getPeriodCredit());
            totalClosingDebit = totalClosingDebit.add(line.getClosingDebit());
            totalClosingCredit = totalClosingCredit.add(line.getClosingCredit());
        }

        return new TrialBalanceTotals(
                totalOpeningDebit,
                totalOpeningCredit,
                totalPeriodDebit,
                totalPeriodCredit,
                totalClosingDebit,
                totalClosingCredit);
    }

    private ArrayList<FinanceStatementLineItem> filterLines(ArrayList<FinanceStatementLineItem> lines,
                                                            String accountType) {
        ArrayList<FinanceStatementLineItem> filtered = new ArrayList<>();
        for (FinanceStatementLineItem line : lines) {
            if (accountType.equals(line.getAccountType())) {
                filtered.add(line);
            }
        }
        return filtered;
    }

    private BigDecimal sumAmounts(ArrayList<FinanceStatementLineItem> lines) {
        BigDecimal total = ZERO;
        for (FinanceStatementLineItem line : lines) {
            total = total.add(line.getAmount());
        }
        return total;
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

    private void requireBranchIfPresent(int companyId, Integer branchId) {
        if (branchId == null) {
            return;
        }

        if (!dbFinanceSetup.branchBelongsToCompany(companyId, branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_BRANCH_SCOPE_INVALID",
                    "Branch does not belong to the requested company");
        }
    }

    private void validateCurrencyCode(String currencyCode) {
        if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }
    }

    private BigDecimal normalBalanceAmount(String normalBalance, BigDecimal debitAmount, BigDecimal creditAmount) {
        return "credit".equals(normalBalance)
                ? creditAmount.subtract(debitAmount)
                : debitAmount.subtract(creditAmount);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_REPORT_LIMIT_INVALID",
                    "Limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_REPORT_OFFSET_INVALID",
                    "Offset must be zero or greater");
        }
        return offset;
    }

    private void authorizeRead(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.read");
    }

    private record TrialBalanceTotals(BigDecimal totalOpeningDebit,
                                      BigDecimal totalOpeningCredit,
                                      BigDecimal totalPeriodDebit,
                                      BigDecimal totalPeriodCredit,
                                      BigDecimal totalClosingDebit,
                                      BigDecimal totalClosingCredit) {
    }
}
