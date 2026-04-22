package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalPeriodItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalYearItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceSetupBundleResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceSetupOverviewResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTaxCodeItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountMappingCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountMappingUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalPeriodCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalPeriodUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalYearCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalYearUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceTaxCodeCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceTaxCodeUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FinanceSetupService {

    private static final Set<String> FISCAL_YEAR_STATUSES = Set.of("planned", "open", "closing", "closed", "archived");
    private static final Set<String> FISCAL_PERIOD_STATUSES = Set.of("planned", "open", "soft_locked", "hard_closed");
    private static final Set<String> ACCOUNT_TYPES = Set.of("asset", "liability", "equity", "revenue", "expense");
    private static final Set<String> NORMAL_BALANCES = Set.of("debit", "credit");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("active", "inactive", "archived");
    private static final Set<String> ACCOUNT_MAPPING_STATUSES = Set.of("active", "inactive", "archived");
    private static final Pattern ACCOUNT_MAPPING_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");
    private static final Set<String> TAX_CODE_STATUSES = Set.of("active", "inactive", "archived");
    private static final Set<String> TAX_TYPES = Set.of("sales_vat", "purchase_vat", "withholding", "exempt", "zero_rated");
    private static final Pattern TAX_CODE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]*$");

    private final DbFinanceSetup dbFinanceSetup;
    private final AuthorizationService authorizationService;

    public FinanceSetupService(DbFinanceSetup dbFinanceSetup,
            AuthorizationService authorizationService) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.authorizationService = authorizationService;
    }

    public FinanceSetupOverviewResponse getOverviewForAuthenticatedUser(String authenticatedName, int companyId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId);
        return dbFinanceSetup.getOverview(companyId);
    }

    public FinanceSetupBundleResponse getSetupBundleForAuthenticatedUser(String authenticatedName, int companyId,
            Integer branchId) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId);

        return new FinanceSetupBundleResponse(
                companyId,
                dbFinanceSetup.getOverview(companyId),
                dbFinanceSetup.getFiscalYears(companyId),
                dbFinanceSetup.getFiscalPeriods(companyId),
                dbFinanceSetup.getAccounts(companyId),
                dbFinanceSetup.getAccountMappings(companyId, branchId),
                dbFinanceSetup.getTaxCodes(companyId),
                Instant.now());
    }

    public ArrayList<FinanceFiscalYearItem> getFiscalYearsForAuthenticatedUser(String authenticatedName,
            int companyId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId);
        return dbFinanceSetup.getFiscalYears(companyId);
    }

    public ArrayList<FinanceFiscalPeriodItem> getFiscalPeriodsForAuthenticatedUser(String authenticatedName,
            int companyId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId);
        return dbFinanceSetup.getFiscalPeriods(companyId);
    }

    public ArrayList<FinanceAccountItem> getAccountsForAuthenticatedUser(String authenticatedName, int companyId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId);
        return dbFinanceSetup.getAccounts(companyId);
    }

    public ArrayList<FinanceAccountMappingItem> getAccountMappingsForAuthenticatedUser(String authenticatedName,
            int companyId, Integer branchId) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId);
        return dbFinanceSetup.getAccountMappings(companyId, branchId);
    }

    public ArrayList<FinanceTaxCodeItem> getTaxCodesForAuthenticatedUser(String authenticatedName, int companyId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId);
        return dbFinanceSetup.getTaxCodes(companyId);
    }

    public FinanceTaxCodeItem createTaxCodeForAuthenticatedUser(String authenticatedName,
            FinanceTaxCodeCreateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeEdit(authenticatedName, request.getCompanyId());
        validateTaxCodeRequest(
                request.getCompanyId(),
                request.getCode(),
                request.getName(),
                request.getRate(),
                request.getTaxType(),
                request.getOutputAccountId(),
                request.getInputAccountId(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                request.getStatus(),
                null);

        return dbFinanceSetup.createTaxCode(request);
    }

    public FinanceTaxCodeItem updateTaxCodeForAuthenticatedUser(String authenticatedName,
            UUID taxCodeId,
            FinanceTaxCodeUpdateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeEdit(authenticatedName, request.getCompanyId());

        if (!dbFinanceSetup.taxCodeExists(request.getCompanyId(), taxCodeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_TAX_CODE_NOT_FOUND",
                    "Tax code does not exist");
        }

        validateTaxCodeRequest(
                request.getCompanyId(),
                request.getCode(),
                request.getName(),
                request.getRate(),
                request.getTaxType(),
                request.getOutputAccountId(),
                request.getInputAccountId(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                request.getStatus(),
                taxCodeId);

        FinanceTaxCodeItem updated = dbFinanceSetup.updateTaxCode(taxCodeId, request);
        if (updated == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_VERSION_CONFLICT",
                    "Tax code was updated by another request");
        }
        return updated;
    }

    public FinanceAccountMappingItem createAccountMappingForAuthenticatedUser(String authenticatedName,
            FinanceAccountMappingCreateRequest request) {
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        authorizeEdit(authenticatedName, request.getCompanyId());
        validateAccountMappingRequest(
                request.getCompanyId(),
                request.getBranchId(),
                request.getMappingKey(),
                request.getAccountId(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                request.getStatus(),
                null);

        return dbFinanceSetup.createAccountMapping(request);
    }

    public FinanceAccountMappingItem updateAccountMappingForAuthenticatedUser(String authenticatedName,
            UUID accountMappingId,
            FinanceAccountMappingUpdateRequest request) {
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        authorizeEdit(authenticatedName, request.getCompanyId());

        if (!dbFinanceSetup.accountMappingExists(request.getCompanyId(), accountMappingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_ACCOUNT_MAPPING_NOT_FOUND",
                    "Account mapping does not exist");
        }

        validateAccountMappingRequest(
                request.getCompanyId(),
                request.getBranchId(),
                request.getMappingKey(),
                request.getAccountId(),
                request.getEffectiveFrom(),
                request.getEffectiveTo(),
                request.getStatus(),
                accountMappingId);

        FinanceAccountMappingItem updated = dbFinanceSetup.updateAccountMapping(accountMappingId, request);
        if (updated == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_VERSION_CONFLICT",
                    "Account mapping was updated by another request");
        }
        return updated;
    }

    public FinanceAccountItem createAccountForAuthenticatedUser(String authenticatedName,
            FinanceAccountCreateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeEdit(authenticatedName, request.getCompanyId());
        validateAccountRequest(
                request.getCompanyId(),
                request.getAccountCode(),
                request.getAccountType(),
                request.getNormalBalance(),
                request.getStatus(),
                request.getParentAccountId(),
                request.isPostable(),
                null);

        AccountHierarchy hierarchy = resolveAccountHierarchy(request.getCompanyId(), request.getParentAccountId(),
                request.getAccountCode());
        return dbFinanceSetup.createAccount(request, hierarchy.accountPath(), hierarchy.accountLevel());
    }

    public FinanceAccountItem updateAccountForAuthenticatedUser(String authenticatedName,
            UUID accountId,
            FinanceAccountUpdateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeEdit(authenticatedName, request.getCompanyId());

        if (!dbFinanceSetup.accountExists(request.getCompanyId(), accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_ACCOUNT_NOT_FOUND", "Account does not exist");
        }

        FinanceAccountItem existing = dbFinanceSetup.getAccountById(request.getCompanyId(), accountId);
        validateAccountRequest(
                request.getCompanyId(),
                request.getAccountCode(),
                request.getAccountType(),
                request.getNormalBalance(),
                request.getStatus(),
                request.getParentAccountId(),
                request.isPostable(),
                accountId);

        if (request.getParentAccountId() != null && request.getParentAccountId().equals(accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_PARENT_INVALID",
                    "Account cannot be its own parent");
        }

        AccountHierarchy hierarchy = resolveAccountHierarchy(request.getCompanyId(), request.getParentAccountId(),
                request.getAccountCode());

        if (hierarchy.accountPath().startsWith(existing.getAccountPath() + ".")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_HIERARCHY_CYCLE",
                    "Account cannot be moved below one of its descendants");
        }

        FinanceAccountItem updated = dbFinanceSetup.updateAccount(accountId, request, hierarchy.accountPath(),
                hierarchy.accountLevel());
        if (updated == null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_VERSION_CONFLICT",
                    "Account was updated by another request");
        }

        int levelDelta = updated.getAccountLevel() - existing.getAccountLevel();
        if (!existing.getAccountPath().equals(updated.getAccountPath()) || levelDelta != 0) {
            dbFinanceSetup.updateDescendantAccountPaths(
                    request.getCompanyId(),
                    existing.getAccountPath(),
                    updated.getAccountPath(),
                    levelDelta);
        }

        return updated;
    }

    public FinanceFiscalYearItem createFiscalYearForAuthenticatedUser(String authenticatedName,
            FinanceFiscalYearCreateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeCreate(authenticatedName, request.getCompanyId());
        validateFiscalYearRequest(request.getStartDate(), request.getEndDate(), request.getStatus());

        if (dbFinanceSetup.fiscalYearHasOverlap(request.getCompanyId(), request.getStartDate(), request.getEndDate(),
                null)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_FISCAL_YEAR_OVERLAP",
                    "Fiscal year date range overlaps an existing fiscal year");
        }

        return dbFinanceSetup.createFiscalYear(request);
    }

    public FinanceFiscalYearItem updateFiscalYearForAuthenticatedUser(String authenticatedName,
            UUID fiscalYearId,
            FinanceFiscalYearUpdateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeEdit(authenticatedName, request.getCompanyId());
        validateFiscalYearRequest(request.getStartDate(), request.getEndDate(), request.getStatus());

        if (!dbFinanceSetup.fiscalYearExists(request.getCompanyId(), fiscalYearId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_FISCAL_YEAR_NOT_FOUND",
                    "Fiscal year does not exist");
        }

        if (dbFinanceSetup.fiscalYearHasOverlap(request.getCompanyId(), request.getStartDate(), request.getEndDate(),
                fiscalYearId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_FISCAL_YEAR_OVERLAP",
                    "Fiscal year date range overlaps an existing fiscal year");
        }

        FinanceFiscalYearItem updated = dbFinanceSetup.updateFiscalYear(fiscalYearId, request);
        if (updated == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_VERSION_CONFLICT",
                    "Fiscal year was updated by another request");
        }
        return updated;
    }

    public FinanceFiscalPeriodItem createFiscalPeriodForAuthenticatedUser(String authenticatedName,
            FinanceFiscalPeriodCreateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeCreate(authenticatedName, request.getCompanyId());
        validateFiscalPeriodRequest(
                request.getCompanyId(),
                request.getFiscalYearId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                null);

        return dbFinanceSetup.createFiscalPeriod(request);
    }

    public FinanceFiscalPeriodItem updateFiscalPeriodForAuthenticatedUser(String authenticatedName,
            UUID fiscalPeriodId,
            FinanceFiscalPeriodUpdateRequest request) {
        requireCompany(request.getCompanyId());
        authorizeEdit(authenticatedName, request.getCompanyId());

        if (!dbFinanceSetup.fiscalPeriodExists(request.getCompanyId(), fiscalPeriodId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_FISCAL_PERIOD_NOT_FOUND",
                    "Fiscal period does not exist");
        }

        validateFiscalPeriodRequest(
                request.getCompanyId(),
                request.getFiscalYearId(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                fiscalPeriodId);

        FinanceFiscalPeriodItem updated = dbFinanceSetup.updateFiscalPeriod(fiscalPeriodId, request);
        if (updated == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_VERSION_CONFLICT",
                    "Fiscal period was updated by another request");
        }
        return updated;
    }

    private void authorizeRead(String authenticatedName, int companyId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                null,
                "finance.entry.read");
    }

    private void authorizeCreate(String authenticatedName, int companyId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                null,
                "finance.entry.create");
    }

    private void authorizeEdit(String authenticatedName, int companyId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                null,
                "finance.entry.edit");
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private void requireBranchIfPresent(int companyId, Integer branchId) {
        if (branchId == null) {
            return;
        }

        if (!dbFinanceSetup.branchBelongsToCompany(companyId, branchId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FINANCE_BRANCH_SCOPE_INVALID",
                    "Branch does not belong to the requested company");
        }
    }

    private void validateFiscalYearRequest(LocalDate startDate, LocalDate endDate, String status) {
        validateDateRange(startDate, endDate);
        validateStatus(status, FISCAL_YEAR_STATUSES, "FINANCE_FISCAL_YEAR_STATUS_INVALID");
    }

    private void validateFiscalPeriodRequest(int companyId,
            UUID fiscalYearId,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            UUID excludedFiscalPeriodId) {
        validateDateRange(startDate, endDate);
        validateStatus(status, FISCAL_PERIOD_STATUSES, "FINANCE_FISCAL_PERIOD_STATUS_INVALID");

        if (!dbFinanceSetup.fiscalYearExists(companyId, fiscalYearId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_YEAR_INVALID",
                    "Fiscal year does not belong to the company");
        }

        if (!dbFinanceSetup.fiscalPeriodInsideFiscalYear(companyId, fiscalYearId, startDate, endDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FINANCE_FISCAL_PERIOD_OUTSIDE_YEAR",
                    "Fiscal period date range must stay inside the fiscal year");
        }

        if (dbFinanceSetup.fiscalPeriodHasOverlap(companyId, startDate, endDate, excludedFiscalPeriodId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_FISCAL_PERIOD_OVERLAP",
                    "Fiscal period date range overlaps an existing fiscal period");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FINANCE_DATE_RANGE_INVALID",
                    "Start date must be before or equal to end date");
        }
    }

    private void validateOptionalDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || (endDate != null && startDate.isAfter(endDate))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FINANCE_DATE_RANGE_INVALID",
                    "Start date must be before or equal to end date");
        }
    }

    private void validateStatus(String status, Set<String> allowedStatuses, String errorCode) {
        if (status == null || !allowedStatuses.contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    errorCode,
                    "Invalid finance setup status");
        }
    }

    private void validateAccountRequest(int companyId,
            String accountCode,
            String accountType,
            String normalBalance,
            String status,
            UUID parentAccountId,
            boolean postable,
            UUID excludedAccountId) {
        validateStatus(accountType, ACCOUNT_TYPES, "FINANCE_ACCOUNT_TYPE_INVALID");
        validateStatus(normalBalance, NORMAL_BALANCES, "FINANCE_ACCOUNT_NORMAL_BALANCE_INVALID");
        validateStatus(status, ACCOUNT_STATUSES, "FINANCE_ACCOUNT_STATUS_INVALID");

        if (accountCode == null || accountCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_CODE_INVALID", "Account code is required");
        }

        if (dbFinanceSetup.accountCodeExists(companyId, accountCode.trim(), excludedAccountId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_ACCOUNT_CODE_EXISTS",
                    "Account code already exists for this company");
        }

        if (parentAccountId != null && !dbFinanceSetup.accountExists(companyId, parentAccountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_PARENT_INVALID",
                    "Parent account does not belong to the company");
        }

        if (postable && dbFinanceSetup.accountHasChildren(companyId, excludedAccountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_POSTABLE_WITH_CHILDREN",
                    "Account with child accounts cannot be postable");
        }
    }

    private void validateAccountMappingRequest(int companyId,
            Integer branchId,
            String mappingKey,
            UUID accountId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            UUID excludedAccountMappingId) {
        validateStatus(status, ACCOUNT_MAPPING_STATUSES, "FINANCE_ACCOUNT_MAPPING_STATUS_INVALID");
        validateOptionalDateRange(effectiveFrom, effectiveTo);

        if (branchId != null && branchId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_BRANCH_SCOPE_INVALID",
                    "Branch must be positive when provided");
        }

        if (mappingKey == null || !ACCOUNT_MAPPING_KEY_PATTERN.matcher(mappingKey.trim()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_MAPPING_KEY_INVALID",
                    "Mapping key must use lowercase dot-separated identifiers");
        }

        if (!dbFinanceSetup.accountExists(companyId, accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_MAPPING_ACCOUNT_INVALID",
                    "Mapped account does not belong to the company");
        }

        FinanceAccountItem account = dbFinanceSetup.getAccountById(companyId, accountId);
        if ("active".equals(status) && (!account.isPostable() || !"active".equals(account.getStatus()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_ACCOUNT_MAPPING_ACCOUNT_NOT_POSTABLE",
                    "Active account mappings must point to an active postable account");
        }

        if ("active".equals(status) && dbFinanceSetup.accountMappingHasActiveConflict(
                companyId,
                branchId,
                mappingKey.trim(),
                effectiveFrom,
                effectiveTo,
                excludedAccountMappingId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_ACCOUNT_MAPPING_OVERLAP",
                    "Active account mapping overlaps an existing active mapping for the same key and branch scope");
        }
    }

    private void validateTaxCodeRequest(int companyId,
            String code,
            String name,
            BigDecimal rate,
            String taxType,
            UUID outputAccountId,
            UUID inputAccountId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            UUID excludedTaxCodeId) {
        validateStatus(status, TAX_CODE_STATUSES, "FINANCE_TAX_CODE_STATUS_INVALID");
        validateStatus(taxType, TAX_TYPES, "FINANCE_TAX_CODE_TYPE_INVALID");
        validateOptionalDateRange(effectiveFrom, effectiveTo);

        if (code == null || !TAX_CODE_PATTERN.matcher(code.trim()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_CODE_INVALID",
                    "Tax code must start with a letter and contain only letters, numbers, dot, dash, or underscore");
        }

        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_CODE_NAME_INVALID",
                    "Tax code name is required");
        }

        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_RATE_INVALID",
                    "Tax rate must be zero or greater");
        }

        if (("exempt".equals(taxType) || "zero_rated".equals(taxType)) && rate.compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_RATE_INVALID",
                    "Exempt and zero-rated tax codes must use a zero rate");
        }

        boolean active = "active".equals(status);
        if (active && "sales_vat".equals(taxType) && outputAccountId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_OUTPUT_ACCOUNT_REQUIRED",
                    "Active sales VAT tax codes require an output tax account");
        }

        if (active && "purchase_vat".equals(taxType) && inputAccountId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_INPUT_ACCOUNT_REQUIRED",
                    "Active purchase VAT tax codes require an input tax account");
        }

        if (active && "withholding".equals(taxType) && outputAccountId == null && inputAccountId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_TAX_ACCOUNT_REQUIRED",
                    "Active withholding tax codes require at least one tax account");
        }

        validateTaxAccount(companyId, outputAccountId, active, "FINANCE_TAX_OUTPUT_ACCOUNT_INVALID");
        validateTaxAccount(companyId, inputAccountId, active, "FINANCE_TAX_INPUT_ACCOUNT_INVALID");

        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (dbFinanceSetup.taxCodeEffectiveDateExists(companyId, normalizedCode, effectiveFrom, excludedTaxCodeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_TAX_CODE_EFFECTIVE_DATE_EXISTS",
                    "Tax code already exists for this effective date");
        }

        if (active && dbFinanceSetup.taxCodeHasActiveConflict(
                companyId,
                normalizedCode,
                effectiveFrom,
                effectiveTo,
                excludedTaxCodeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_TAX_CODE_OVERLAP",
                    "Active tax code overlaps an existing active tax code with the same code");
        }
    }

    private void validateTaxAccount(int companyId, UUID accountId, boolean activeTaxCode, String errorCode) {
        if (accountId == null) {
            return;
        }

        if (!dbFinanceSetup.accountExists(companyId, accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "Tax account does not belong to the company");
        }

        FinanceAccountItem account = dbFinanceSetup.getAccountById(companyId, accountId);
        if (activeTaxCode && (!account.isPostable() || !"active".equals(account.getStatus()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "Active tax codes must point to active postable accounts");
        }
    }

    private AccountHierarchy resolveAccountHierarchy(int companyId, UUID parentAccountId, String accountCode) {
        String normalizedCode = accountCode.trim();

        if (parentAccountId == null) {
            return new AccountHierarchy(normalizedCode, 0);
        }

        FinanceAccountItem parent = dbFinanceSetup.getAccountById(companyId, parentAccountId);
        return new AccountHierarchy(parent.getAccountPath() + "." + normalizedCode, parent.getAccountLevel() + 1);
    }

    private record AccountHierarchy(String accountPath, int accountLevel) {
    }
}
