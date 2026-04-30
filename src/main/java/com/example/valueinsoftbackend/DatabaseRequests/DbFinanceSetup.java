package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalPeriodItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalYearItem;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Repository
public class DbFinanceSetup {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbFinanceSetup(JdbcTemplate jdbcTemplate,
                          NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public boolean companyExists(int companyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.\"Company\" WHERE id = ?",
                Integer.class,
                companyId
        );
        return count != null && count > 0;
    }

    public boolean branchBelongsToCompany(int companyId, int branchId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.\"Branch\" WHERE \"companyId\" = ? AND \"branchId\" = ?",
                Integer.class,
                companyId,
                branchId
        );
        return count != null && count > 0;
    }

    public FinanceSetupOverviewResponse getOverview(int companyId) {
        MapSqlParameterSource params = new MapSqlParameterSource("companyId", companyId);

        Integer fiscalYearCount = count("SELECT COUNT(*) FROM public.finance_fiscal_year WHERE company_id = :companyId", params);
        Integer openFiscalPeriodCount = count("SELECT COUNT(*) FROM public.finance_fiscal_period WHERE company_id = :companyId AND status = 'open'", params);
        Integer accountCount = count("SELECT COUNT(*) FROM public.finance_account WHERE company_id = :companyId", params);
        Integer activeAccountMappingCount = count("SELECT COUNT(*) FROM public.finance_account_mapping WHERE company_id = :companyId AND status = 'active'", params);
        Integer activeTaxCodeCount = count("SELECT COUNT(*) FROM public.finance_tax_code WHERE company_id = :companyId AND status = 'active'", params);

        boolean hasFiscalCalendar = fiscalYearCount > 0 && openFiscalPeriodCount > 0;
        boolean hasChartOfAccounts = accountCount > 0;
        boolean hasAccountMappings = activeAccountMappingCount > 0;
        boolean hasTaxSetup = activeTaxCodeCount > 0;

        return new FinanceSetupOverviewResponse(
                companyId,
                fiscalYearCount,
                openFiscalPeriodCount,
                accountCount,
                activeAccountMappingCount,
                activeTaxCodeCount,
                hasFiscalCalendar,
                hasChartOfAccounts,
                hasAccountMappings,
                hasTaxSetup,
                hasFiscalCalendar && hasChartOfAccounts && hasAccountMappings,
                Instant.now()
        );
    }

    public boolean fiscalYearExists(int companyId, UUID fiscalYearId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_fiscal_year " +
                        "WHERE company_id = :companyId AND fiscal_year_id = :fiscalYearId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalYearId", fiscalYearId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean fiscalPeriodExists(int companyId, UUID fiscalPeriodId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId AND fiscal_period_id = :fiscalPeriodId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalPeriodId", fiscalPeriodId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean fiscalYearHasOverlap(int companyId, LocalDate startDate, LocalDate endDate, UUID excludedFiscalYearId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("excludedFiscalYearId", excludedFiscalYearId);

        String excludeClause = excludedFiscalYearId == null
                ? ""
                : "AND fiscal_year_id <> :excludedFiscalYearId ";

        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_fiscal_year " +
                        "WHERE company_id = :companyId " +
                        "AND status <> 'archived' " +
                        excludeClause +
                        "AND start_date <= :endDate " +
                        "AND end_date >= :startDate",
                params,
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean fiscalPeriodHasOverlap(int companyId, LocalDate startDate, LocalDate endDate, UUID excludedFiscalPeriodId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("excludedFiscalPeriodId", excludedFiscalPeriodId);

        String excludeClause = excludedFiscalPeriodId == null
                ? ""
                : "AND fiscal_period_id <> :excludedFiscalPeriodId ";

        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId " +
                        excludeClause +
                        "AND start_date <= :endDate " +
                        "AND end_date >= :startDate",
                params,
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean fiscalPeriodInsideFiscalYear(int companyId, UUID fiscalYearId, LocalDate startDate, LocalDate endDate) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_fiscal_year " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_year_id = :fiscalYearId " +
                        "AND start_date <= :startDate " +
                        "AND end_date >= :endDate",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalYearId", fiscalYearId)
                        .addValue("startDate", startDate)
                        .addValue("endDate", endDate),
                Integer.class
        );
        return count != null && count > 0;
    }

    public FinanceFiscalYearItem createFiscalYear(FinanceFiscalYearCreateRequest request) {
        UUID fiscalYearId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_fiscal_year " +
                        "(company_id, name, start_date, end_date, base_currency_code, status) " +
                        "VALUES (:companyId, :name, :startDate, :endDate, :baseCurrencyCode, :status) " +
                        "RETURNING fiscal_year_id",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("name", request.getName().trim())
                        .addValue("startDate", request.getStartDate())
                        .addValue("endDate", request.getEndDate())
                        .addValue("baseCurrencyCode", request.getBaseCurrencyCode())
                        .addValue("status", request.getStatus()),
                UUID.class
        );
        return getFiscalYearById(request.getCompanyId(), fiscalYearId);
    }

    public FinanceFiscalYearItem updateFiscalYear(UUID fiscalYearId, FinanceFiscalYearUpdateRequest request) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_fiscal_year " +
                        "SET name = :name, " +
                        "start_date = :startDate, " +
                        "end_date = :endDate, " +
                        "base_currency_code = :baseCurrencyCode, " +
                        "status = :status, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_year_id = :fiscalYearId " +
                        "AND version = :version",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("fiscalYearId", fiscalYearId)
                        .addValue("name", request.getName().trim())
                        .addValue("startDate", request.getStartDate())
                        .addValue("endDate", request.getEndDate())
                        .addValue("baseCurrencyCode", request.getBaseCurrencyCode())
                        .addValue("status", request.getStatus())
                        .addValue("version", request.getVersion())
        );
        return rows == 0 ? null : getFiscalYearById(request.getCompanyId(), fiscalYearId);
    }

    public int deleteFiscalYear(int companyId, UUID fiscalYearId) {
        return namedParameterJdbcTemplate.update(
                "DELETE FROM public.finance_fiscal_year " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_year_id = :fiscalYearId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalYearId", fiscalYearId)
        );
    }

    public FinanceFiscalPeriodItem createFiscalPeriod(FinanceFiscalPeriodCreateRequest request) {
        UUID fiscalPeriodId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_fiscal_period " +
                        "(company_id, fiscal_year_id, period_number, name, start_date, end_date, status) " +
                        "VALUES (:companyId, :fiscalYearId, :periodNumber, :name, :startDate, :endDate, :status) " +
                        "RETURNING fiscal_period_id",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("fiscalYearId", request.getFiscalYearId())
                        .addValue("periodNumber", request.getPeriodNumber())
                        .addValue("name", request.getName().trim())
                        .addValue("startDate", request.getStartDate())
                        .addValue("endDate", request.getEndDate())
                        .addValue("status", request.getStatus()),
                UUID.class
        );
        return getFiscalPeriodById(request.getCompanyId(), fiscalPeriodId);
    }

    public FinanceFiscalPeriodItem updateFiscalPeriod(UUID fiscalPeriodId, FinanceFiscalPeriodUpdateRequest request) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_fiscal_period " +
                        "SET fiscal_year_id = :fiscalYearId, " +
                        "period_number = :periodNumber, " +
                        "name = :name, " +
                        "start_date = :startDate, " +
                        "end_date = :endDate, " +
                        "status = :status, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId " +
                        "AND version = :version",
                new MapSqlParameterSource()
                        .addValue("companyId", request.getCompanyId())
                        .addValue("fiscalPeriodId", fiscalPeriodId)
                        .addValue("fiscalYearId", request.getFiscalYearId())
                        .addValue("periodNumber", request.getPeriodNumber())
                        .addValue("name", request.getName().trim())
                        .addValue("startDate", request.getStartDate())
                        .addValue("endDate", request.getEndDate())
                        .addValue("status", request.getStatus())
                        .addValue("version", request.getVersion())
        );
        return rows == 0 ? null : getFiscalPeriodById(request.getCompanyId(), fiscalPeriodId);
    }

    public int deleteFiscalPeriod(int companyId, UUID fiscalPeriodId) {
        return namedParameterJdbcTemplate.update(
                "DELETE FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId " +
                        "AND fiscal_period_id = :fiscalPeriodId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalPeriodId", fiscalPeriodId)
        );
    }

    public boolean fiscalYearHasPeriods(int companyId, UUID fiscalYearId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId AND fiscal_year_id = :fiscalYearId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalYearId", fiscalYearId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean accountExists(int companyId, UUID accountId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_account " +
                        "WHERE company_id = :companyId AND account_id = :accountId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean accountCodeExists(int companyId, String accountCode, UUID excludedAccountId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("accountCode", accountCode)
                .addValue("excludedAccountId", excludedAccountId);

        String excludeClause = excludedAccountId == null
                ? ""
                : "AND account_id <> :excludedAccountId ";

        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_account " +
                        "WHERE company_id = :companyId AND account_code = :accountCode " +
                        excludeClause,
                params,
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean accountHasChildren(int companyId, UUID accountId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_account " +
                        "WHERE company_id = :companyId AND parent_account_id = :accountId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountId", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public FinanceAccountItem createAccount(FinanceAccountCreateRequest request,
                                            String accountPath,
                                            int accountLevel) {
        UUID accountId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_account " +
                        "(company_id, account_code, account_name, account_type, normal_balance, parent_account_id, " +
                        "account_path, account_level, is_postable, is_system, status, currency_code, requires_branch, " +
                        "requires_customer, requires_supplier, requires_product, requires_cost_center) " +
                        "VALUES (:companyId, :accountCode, :accountName, :accountType, :normalBalance, :parentAccountId, " +
                        ":accountPath, :accountLevel, :postable, :system, :status, :currencyCode, :requiresBranch, " +
                        ":requiresCustomer, :requiresSupplier, :requiresProduct, :requiresCostCenter) " +
                        "RETURNING account_id",
                accountParams(request)
                        .addValue("accountPath", accountPath)
                        .addValue("accountLevel", accountLevel),
                UUID.class
        );
        return getAccountById(request.getCompanyId(), accountId);
    }

    public FinanceAccountItem updateAccount(UUID accountId,
                                            FinanceAccountUpdateRequest request,
                                            String accountPath,
                                            int accountLevel) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_account " +
                        "SET account_code = :accountCode, " +
                        "account_name = :accountName, " +
                        "account_type = :accountType, " +
                        "normal_balance = :normalBalance, " +
                        "parent_account_id = :parentAccountId, " +
                        "account_path = :accountPath, " +
                        "account_level = :accountLevel, " +
                        "is_postable = :postable, " +
                        "is_system = :system, " +
                        "status = :status, " +
                        "currency_code = :currencyCode, " +
                        "requires_branch = :requiresBranch, " +
                        "requires_customer = :requiresCustomer, " +
                        "requires_supplier = :requiresSupplier, " +
                        "requires_product = :requiresProduct, " +
                        "requires_cost_center = :requiresCostCenter, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND account_id = :accountId " +
                        "AND version = :version",
                accountParams(request)
                        .addValue("accountId", accountId)
                        .addValue("accountPath", accountPath)
                        .addValue("accountLevel", accountLevel)
                        .addValue("version", request.getVersion())
        );
        return rows == 0 ? null : getAccountById(request.getCompanyId(), accountId);
    }

    public int deleteAccount(int companyId, UUID accountId) {
        return namedParameterJdbcTemplate.update(
                "DELETE FROM public.finance_account " +
                        "WHERE company_id = :companyId " +
                        "AND account_id = :accountId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountId", accountId)
        );
    }

    public int updateDescendantAccountPaths(int companyId, String oldPathPrefix, String newPathPrefix, int levelDelta) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.finance_account " +
                        "SET account_path = :newPathPrefix || substring(account_path from (:oldPrefixLength + 1)), " +
                        "account_level = account_level + :levelDelta, " +
                        "version = version + 1, " +
                        "updated_at = NOW() " +
                        "WHERE company_id = :companyId " +
                        "AND account_path LIKE :oldPathLike " +
                        "AND account_path <> :oldPathPrefix",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("oldPathPrefix", oldPathPrefix)
                        .addValue("newPathPrefix", newPathPrefix)
                        .addValue("oldPathLike", oldPathPrefix + ".%")
                        .addValue("oldPrefixLength", oldPathPrefix.length())
                        .addValue("levelDelta", levelDelta)
        );
    }

    public boolean accountMappingExists(int companyId, UUID accountMappingId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_account_mapping " +
                        "WHERE company_id = :companyId AND account_mapping_id = :accountMappingId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountMappingId", accountMappingId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean accountMappingHasActiveConflict(int companyId,
                                                   Integer branchId,
                                                   Integer supplierId,
                                                   String mappingKey,
                                                   LocalDate effectiveFrom,
                                                   LocalDate effectiveTo,
                                                   UUID excludedAccountMappingId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("mappingKey", mappingKey)
                .addValue("effectiveFrom", effectiveFrom)
                .addValue("effectiveTo", effectiveTo)
                .addValue("excludedAccountMappingId", excludedAccountMappingId);

        String branchClause;
        if (branchId == null) {
            branchClause = "AND branch_id IS NULL ";
        } else {
            branchClause = "AND branch_id = :branchId ";
            params.addValue("branchId", branchId);
        }

        String supplierClause;
        if (supplierId == null) {
            supplierClause = "AND supplier_id IS NULL ";
        } else {
            supplierClause = "AND supplier_id = :supplierId ";
            params.addValue("supplierId", supplierId);
        }

        String excludeClause = excludedAccountMappingId == null
                ? ""
                : "AND account_mapping_id <> :excludedAccountMappingId ";

        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_account_mapping " +
                        "WHERE company_id = :companyId " +
                        "AND mapping_key = :mappingKey " +
                        "AND status = 'active' " +
                        branchClause +
                        supplierClause +
                        excludeClause +
                        "AND effective_from <= COALESCE(:effectiveTo, DATE '9999-12-31') " +
                        "AND COALESCE(effective_to, DATE '9999-12-31') >= :effectiveFrom",
                params,
                Integer.class
        );
        return count != null && count > 0;
    }

    public FinanceAccountMappingItem createAccountMapping(FinanceAccountMappingCreateRequest request) {
        UUID accountMappingId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_account_mapping " +
                        "(company_id, branch_id, supplier_id, mapping_key, account_id, priority, effective_from, effective_to, status) " +
                        "VALUES (:companyId, :branchId, :supplierId, :mappingKey, :accountId, :priority, :effectiveFrom, :effectiveTo, :status) " +
                        "RETURNING account_mapping_id",
                accountMappingParams(request),
                UUID.class
        );
        return getAccountMappingById(request.getCompanyId(), accountMappingId);
    }

    public FinanceAccountMappingItem updateAccountMapping(UUID accountMappingId, FinanceAccountMappingUpdateRequest request) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_account_mapping " +
                        "SET branch_id = :branchId, " +
                        "supplier_id = :supplierId, " +
                        "mapping_key = :mappingKey, " +
                        "account_id = :accountId, " +
                        "priority = :priority, " +
                        "effective_from = :effectiveFrom, " +
                        "effective_to = :effectiveTo, " +
                        "status = :status, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND account_mapping_id = :accountMappingId " +
                        "AND version = :version",
                accountMappingParams(request)
                        .addValue("accountMappingId", accountMappingId)
                        .addValue("version", request.getVersion())
        );
        return rows == 0 ? null : getAccountMappingById(request.getCompanyId(), accountMappingId);
    }

    public int deleteAccountMapping(int companyId, UUID accountMappingId) {
        return namedParameterJdbcTemplate.update(
                "DELETE FROM public.finance_account_mapping " +
                        "WHERE company_id = :companyId " +
                        "AND account_mapping_id = :accountMappingId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountMappingId", accountMappingId)
        );
    }

    public boolean taxCodeExists(int companyId, UUID taxCodeId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId AND tax_code_id = :taxCodeId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("taxCodeId", taxCodeId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean taxCodeEffectiveDateExists(int companyId,
                                              String code,
                                              LocalDate effectiveFrom,
                                              UUID excludedTaxCodeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("code", code)
                .addValue("effectiveFrom", effectiveFrom)
                .addValue("excludedTaxCodeId", excludedTaxCodeId);

        String excludeClause = excludedTaxCodeId == null
                ? ""
                : "AND tax_code_id <> :excludedTaxCodeId ";

        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId " +
                        "AND code = :code " +
                        "AND effective_from = :effectiveFrom " +
                        excludeClause,
                params,
                Integer.class
        );
        return count != null && count > 0;
    }

    public boolean taxCodeHasActiveConflict(int companyId,
                                            String code,
                                            LocalDate effectiveFrom,
                                            LocalDate effectiveTo,
                                            UUID excludedTaxCodeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("code", code)
                .addValue("effectiveFrom", effectiveFrom)
                .addValue("effectiveTo", effectiveTo)
                .addValue("excludedTaxCodeId", excludedTaxCodeId);

        String excludeClause = excludedTaxCodeId == null
                ? ""
                : "AND tax_code_id <> :excludedTaxCodeId ";

        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId " +
                        "AND code = :code " +
                        "AND status = 'active' " +
                        excludeClause +
                        "AND effective_from <= COALESCE(:effectiveTo, DATE '9999-12-31') " +
                        "AND COALESCE(effective_to, DATE '9999-12-31') >= :effectiveFrom",
                params,
                Integer.class
        );
        return count != null && count > 0;
    }

    public FinanceTaxCodeItem createTaxCode(FinanceTaxCodeCreateRequest request) {
        UUID taxCodeId = namedParameterJdbcTemplate.queryForObject(
                "INSERT INTO public.finance_tax_code " +
                        "(company_id, code, name, rate, tax_type, output_account_id, input_account_id, effective_from, effective_to, status) " +
                        "VALUES (:companyId, :code, :name, :rate, :taxType, :outputAccountId, :inputAccountId, :effectiveFrom, :effectiveTo, :status) " +
                        "RETURNING tax_code_id",
                taxCodeParams(request),
                UUID.class
        );
        return getTaxCodeById(request.getCompanyId(), taxCodeId);
    }

    public FinanceTaxCodeItem updateTaxCode(UUID taxCodeId, FinanceTaxCodeUpdateRequest request) {
        int rows = namedParameterJdbcTemplate.update(
                "UPDATE public.finance_tax_code " +
                        "SET code = :code, " +
                        "name = :name, " +
                        "rate = :rate, " +
                        "tax_type = :taxType, " +
                        "output_account_id = :outputAccountId, " +
                        "input_account_id = :inputAccountId, " +
                        "effective_from = :effectiveFrom, " +
                        "effective_to = :effectiveTo, " +
                        "status = :status, " +
                        "version = version + 1 " +
                        "WHERE company_id = :companyId " +
                        "AND tax_code_id = :taxCodeId " +
                        "AND version = :version",
                taxCodeParams(request)
                        .addValue("taxCodeId", taxCodeId)
                        .addValue("version", request.getVersion())
        );
        return rows == 0 ? null : getTaxCodeById(request.getCompanyId(), taxCodeId);
    }

    public int deleteTaxCode(int companyId, UUID taxCodeId) {
        return namedParameterJdbcTemplate.update(
                "DELETE FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId " +
                        "AND tax_code_id = :taxCodeId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("taxCodeId", taxCodeId)
        );
    }

    public ArrayList<FinanceFiscalYearItem> getFiscalYears(int companyId) {
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT fiscal_year_id, company_id, name, start_date, end_date, base_currency_code, status, version, " +
                        "created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_fiscal_year " +
                        "WHERE company_id = :companyId " +
                        "ORDER BY start_date DESC, name ASC",
                new MapSqlParameterSource("companyId", companyId),
                (rs, rowNum) -> mapFiscalYear(rs)
        ));
    }

    public FinanceFiscalYearItem getFiscalYearById(int companyId, UUID fiscalYearId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT fiscal_year_id, company_id, name, start_date, end_date, base_currency_code, status, version, " +
                        "created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_fiscal_year " +
                        "WHERE company_id = :companyId AND fiscal_year_id = :fiscalYearId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalYearId", fiscalYearId),
                (rs, rowNum) -> mapFiscalYear(rs)
        );
    }

    public ArrayList<FinanceFiscalPeriodItem> getFiscalPeriods(int companyId) {
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT fiscal_period_id, company_id, fiscal_year_id, period_number, name, start_date, end_date, status, " +
                        "locked_at, locked_by, closed_at, closed_by, version, created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId " +
                        "ORDER BY start_date DESC, period_number DESC",
                new MapSqlParameterSource("companyId", companyId),
                (rs, rowNum) -> mapFiscalPeriod(rs)
        ));
    }

    public UUID findPostingFiscalPeriodIdForDate(int companyId, LocalDate postingDate) {
        ArrayList<UUID> periodIds = new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT fiscal_period_id " +
                        "FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId " +
                        "AND start_date <= :postingDate " +
                        "AND end_date >= :postingDate " +
                        "AND status IN ('open', 'soft_locked') " +
                        "ORDER BY CASE WHEN status = 'open' THEN 0 ELSE 1 END, start_date DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("postingDate", postingDate),
                (rs, rowNum) -> uuid(rs, "fiscal_period_id")
        ));
        return periodIds.isEmpty() ? null : periodIds.getFirst();
    }

    public FinanceFiscalPeriodItem getFiscalPeriodById(int companyId, UUID fiscalPeriodId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT fiscal_period_id, company_id, fiscal_year_id, period_number, name, start_date, end_date, status, " +
                        "locked_at, locked_by, closed_at, closed_by, version, created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_fiscal_period " +
                        "WHERE company_id = :companyId AND fiscal_period_id = :fiscalPeriodId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("fiscalPeriodId", fiscalPeriodId),
                (rs, rowNum) -> mapFiscalPeriod(rs)
        );
    }

    public ArrayList<FinanceAccountItem> getAccounts(int companyId) {
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT account_id, company_id, account_code, account_name, account_type, normal_balance, parent_account_id, " +
                        "account_path, account_level, is_postable, is_system, status, currency_code, requires_branch, " +
                        "requires_customer, requires_supplier, requires_product, requires_cost_center, version, " +
                        "created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_account " +
                        "WHERE company_id = :companyId " +
                        "ORDER BY account_path ASC, account_code ASC",
                new MapSqlParameterSource("companyId", companyId),
                (rs, rowNum) -> mapAccount(rs)
        ));
    }

    public FinanceAccountItem getAccountById(int companyId, UUID accountId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT account_id, company_id, account_code, account_name, account_type, normal_balance, parent_account_id, " +
                        "account_path, account_level, is_postable, is_system, status, currency_code, requires_branch, " +
                        "requires_customer, requires_supplier, requires_product, requires_cost_center, version, " +
                        "created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_account " +
                        "WHERE company_id = :companyId AND account_id = :accountId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountId", accountId),
                (rs, rowNum) -> mapAccount(rs)
        );
    }

    public ArrayList<FinanceAccountMappingItem> getAccountMappings(int companyId, Integer branchId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId);

        String branchFilter = branchId == null
                ? ""
                : "AND (m.branch_id IS NULL OR m.branch_id = :branchId) ";

        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT m.account_mapping_id, m.company_id, m.branch_id, m.supplier_id, m.mapping_key, m.account_id, " +
                        "a.account_code, a.account_name, m.priority, m.effective_from, m.effective_to, m.status, " +
                        "m.version, m.created_at, m.created_by, m.updated_at, m.updated_by " +
                        "FROM public.finance_account_mapping m " +
                        "JOIN public.finance_account a ON a.company_id = m.company_id AND a.account_id = m.account_id " +
                        "WHERE m.company_id = :companyId " +
                        branchFilter +
                        "ORDER BY m.mapping_key ASC, m.branch_id NULLS FIRST, m.supplier_id NULLS FIRST, m.priority ASC, m.effective_from DESC",
                params,
                (rs, rowNum) -> mapAccountMapping(rs)
        ));
    }

    public FinanceAccountMappingItem getAccountMappingById(int companyId, UUID accountMappingId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT m.account_mapping_id, m.company_id, m.branch_id, m.supplier_id, m.mapping_key, m.account_id, " +
                        "a.account_code, a.account_name, m.priority, m.effective_from, m.effective_to, m.status, " +
                        "m.version, m.created_at, m.created_by, m.updated_at, m.updated_by " +
                        "FROM public.finance_account_mapping m " +
                        "JOIN public.finance_account a ON a.company_id = m.company_id AND a.account_id = m.account_id " +
                        "WHERE m.company_id = :companyId AND m.account_mapping_id = :accountMappingId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("accountMappingId", accountMappingId),
                (rs, rowNum) -> mapAccountMapping(rs)
        );
    }

    public FinanceAccountMappingItem resolveActiveAccountMapping(int companyId,
                                                                 Integer branchId,
                                                                 Integer supplierId,
                                                                 String mappingKey,
                                                                 LocalDate effectiveDate) {
        ArrayList<FinanceAccountMappingItem> mappings = new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT m.account_mapping_id, m.company_id, m.branch_id, m.supplier_id, m.mapping_key, m.account_id, " +
                        "a.account_code, a.account_name, m.priority, m.effective_from, m.effective_to, m.status, " +
                        "m.version, m.created_at, m.created_by, m.updated_at, m.updated_by " +
                        "FROM public.finance_account_mapping m " +
                        "JOIN public.finance_account a ON a.company_id = m.company_id AND a.account_id = m.account_id " +
                        "WHERE m.company_id = :companyId " +
                        "AND m.mapping_key = :mappingKey " +
                        "AND m.status = 'active' " +
                        "AND m.effective_from <= :effectiveDate " +
                        "AND (m.effective_to IS NULL OR m.effective_to >= :effectiveDate) " +
                        "AND (m.branch_id = :branchId OR m.branch_id IS NULL) " +
                        "AND (m.supplier_id = :supplierId OR m.supplier_id IS NULL) " +
                        "AND a.status = 'active' " +
                        "AND a.is_postable = TRUE " +
                        "ORDER BY " +
                        "  CASE WHEN m.supplier_id = :supplierId THEN 0 ELSE 1 END, " +
                        "  CASE WHEN m.branch_id = :branchId THEN 0 ELSE 1 END, " +
                        "  m.priority ASC, m.effective_from DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("supplierId", supplierId)
                        .addValue("mappingKey", mappingKey)
                        .addValue("effectiveDate", effectiveDate),
                (rs, rowNum) -> mapAccountMapping(rs)));
        return mappings.isEmpty() ? null : mappings.getFirst();
    }

    public ArrayList<FinanceTaxCodeItem> getTaxCodes(int companyId) {
        return new ArrayList<>(namedParameterJdbcTemplate.query(
                "SELECT tax_code_id, company_id, code, name, rate, tax_type, output_account_id, input_account_id, " +
                        "effective_from, effective_to, status, version, created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId " +
                        "ORDER BY code ASC, effective_from DESC",
                new MapSqlParameterSource("companyId", companyId),
                (rs, rowNum) -> mapTaxCode(rs)
        ));
    }

    public FinanceTaxCodeItem getTaxCodeById(int companyId, UUID taxCodeId) {
        return namedParameterJdbcTemplate.queryForObject(
                "SELECT tax_code_id, company_id, code, name, rate, tax_type, output_account_id, input_account_id, " +
                        "effective_from, effective_to, status, version, created_at, created_by, updated_at, updated_by " +
                        "FROM public.finance_tax_code " +
                        "WHERE company_id = :companyId AND tax_code_id = :taxCodeId",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("taxCodeId", taxCodeId),
                (rs, rowNum) -> mapTaxCode(rs)
        );
    }

    private int count(String sql, MapSqlParameterSource params) {
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        return count == null ? 0 : count;
    }

    private FinanceFiscalYearItem mapFiscalYear(ResultSet rs) throws SQLException {
        return new FinanceFiscalYearItem(
                uuid(rs, "fiscal_year_id"),
                rs.getInt("company_id"),
                rs.getString("name"),
                localDate(rs, "start_date"),
                localDate(rs, "end_date"),
                rs.getString("base_currency_code"),
                rs.getString("status"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by")
        );
    }

    private FinanceFiscalPeriodItem mapFiscalPeriod(ResultSet rs) throws SQLException {
        return new FinanceFiscalPeriodItem(
                uuid(rs, "fiscal_period_id"),
                rs.getInt("company_id"),
                uuid(rs, "fiscal_year_id"),
                rs.getInt("period_number"),
                rs.getString("name"),
                localDate(rs, "start_date"),
                localDate(rs, "end_date"),
                rs.getString("status"),
                instant(rs, "locked_at"),
                nullableInteger(rs, "locked_by"),
                instant(rs, "closed_at"),
                nullableInteger(rs, "closed_by"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by")
        );
    }

    private FinanceAccountItem mapAccount(ResultSet rs) throws SQLException {
        return new FinanceAccountItem(
                uuid(rs, "account_id"),
                rs.getInt("company_id"),
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getString("account_type"),
                rs.getString("normal_balance"),
                uuid(rs, "parent_account_id"),
                rs.getString("account_path"),
                rs.getInt("account_level"),
                rs.getBoolean("is_postable"),
                rs.getBoolean("is_system"),
                rs.getString("status"),
                rs.getString("currency_code"),
                rs.getBoolean("requires_branch"),
                rs.getBoolean("requires_customer"),
                rs.getBoolean("requires_supplier"),
                rs.getBoolean("requires_product"),
                rs.getBoolean("requires_cost_center"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by")
        );
    }

    private FinanceAccountMappingItem mapAccountMapping(ResultSet rs) throws SQLException {
        return new FinanceAccountMappingItem(
                uuid(rs, "account_mapping_id"),
                rs.getInt("company_id"),
                nullableInteger(rs, "branch_id"),
                nullableInteger(rs, "supplier_id"),
                rs.getString("mapping_key"),
                uuid(rs, "account_id"),
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getInt("priority"),
                localDate(rs, "effective_from"),
                localDate(rs, "effective_to"),
                rs.getString("status"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by")
        );
    }

    private FinanceTaxCodeItem mapTaxCode(ResultSet rs) throws SQLException {
        return new FinanceTaxCodeItem(
                uuid(rs, "tax_code_id"),
                rs.getInt("company_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("rate"),
                rs.getString("tax_type"),
                uuid(rs, "output_account_id"),
                uuid(rs, "input_account_id"),
                localDate(rs, "effective_from"),
                localDate(rs, "effective_to"),
                rs.getString("status"),
                rs.getInt("version"),
                instant(rs, "created_at"),
                nullableInteger(rs, "created_by"),
                instant(rs, "updated_at"),
                nullableInteger(rs, "updated_by")
        );
    }

    private MapSqlParameterSource accountParams(FinanceAccountCreateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("accountCode", request.getAccountCode().trim())
                .addValue("accountName", request.getAccountName().trim())
                .addValue("accountType", request.getAccountType())
                .addValue("normalBalance", request.getNormalBalance())
                .addValue("parentAccountId", request.getParentAccountId())
                .addValue("postable", request.isPostable())
                .addValue("system", request.isSystem())
                .addValue("status", request.getStatus())
                .addValue("currencyCode", request.getCurrencyCode())
                .addValue("requiresBranch", request.isRequiresBranch())
                .addValue("requiresCustomer", request.isRequiresCustomer())
                .addValue("requiresSupplier", request.isRequiresSupplier())
                .addValue("requiresProduct", request.isRequiresProduct())
                .addValue("requiresCostCenter", request.isRequiresCostCenter());
    }

    private MapSqlParameterSource accountParams(FinanceAccountUpdateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("accountCode", request.getAccountCode().trim())
                .addValue("accountName", request.getAccountName().trim())
                .addValue("accountType", request.getAccountType())
                .addValue("normalBalance", request.getNormalBalance())
                .addValue("parentAccountId", request.getParentAccountId())
                .addValue("postable", request.isPostable())
                .addValue("system", request.isSystem())
                .addValue("status", request.getStatus())
                .addValue("currencyCode", request.getCurrencyCode())
                .addValue("requiresBranch", request.isRequiresBranch())
                .addValue("requiresCustomer", request.isRequiresCustomer())
                .addValue("requiresSupplier", request.isRequiresSupplier())
                .addValue("requiresProduct", request.isRequiresProduct())
                .addValue("requiresCostCenter", request.isRequiresCostCenter());
    }

    private MapSqlParameterSource accountMappingParams(FinanceAccountMappingCreateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("branchId", request.getBranchId())
                .addValue("supplierId", request.getSupplierId())
                .addValue("mappingKey", request.getMappingKey().trim())
                .addValue("accountId", request.getAccountId())
                .addValue("priority", request.getPriority())
                .addValue("effectiveFrom", request.getEffectiveFrom())
                .addValue("effectiveTo", request.getEffectiveTo())
                .addValue("status", request.getStatus());
    }

    private MapSqlParameterSource accountMappingParams(FinanceAccountMappingUpdateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("branchId", request.getBranchId())
                .addValue("supplierId", request.getSupplierId())
                .addValue("mappingKey", request.getMappingKey().trim())
                .addValue("accountId", request.getAccountId())
                .addValue("priority", request.getPriority())
                .addValue("effectiveFrom", request.getEffectiveFrom())
                .addValue("effectiveTo", request.getEffectiveTo())
                .addValue("status", request.getStatus());
    }

    private MapSqlParameterSource taxCodeParams(FinanceTaxCodeCreateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("code", request.getCode().trim().toUpperCase(Locale.ROOT))
                .addValue("name", request.getName().trim())
                .addValue("rate", request.getRate())
                .addValue("taxType", request.getTaxType())
                .addValue("outputAccountId", request.getOutputAccountId())
                .addValue("inputAccountId", request.getInputAccountId())
                .addValue("effectiveFrom", request.getEffectiveFrom())
                .addValue("effectiveTo", request.getEffectiveTo())
                .addValue("status", request.getStatus());
    }

    private MapSqlParameterSource taxCodeParams(FinanceTaxCodeUpdateRequest request) {
        return new MapSqlParameterSource()
                .addValue("companyId", request.getCompanyId())
                .addValue("code", request.getCode().trim().toUpperCase(Locale.ROOT))
                .addValue("name", request.getName().trim())
                .addValue("rate", request.getRate())
                .addValue("taxType", request.getTaxType())
                .addValue("outputAccountId", request.getOutputAccountId())
                .addValue("inputAccountId", request.getInputAccountId())
                .addValue("effectiveFrom", request.getEffectiveFrom())
                .addValue("effectiveTo", request.getEffectiveTo())
                .addValue("status", request.getStatus());
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
