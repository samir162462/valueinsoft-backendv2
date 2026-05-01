package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Payroll.*;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DbPayroll {

    private final JdbcTemplate jdbcTemplate;

    public DbPayroll(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PayrollSettings> settingsMapper = (rs, rowNum) -> new PayrollSettings(
            rs.getInt("id"),
            rs.getInt("company_id"),
            rs.getString("default_currency"),
            rs.getString("default_frequency"),
            rs.getBoolean("auto_include_attendance"),
            rs.getBigDecimal("overtime_rate_multiplier"),
            rs.getBigDecimal("late_deduction_per_minute"),
            uuid(rs, "salary_expense_account_id"),
            uuid(rs, "salary_payable_account_id"),
            uuid(rs, "cash_bank_account_id"),
            rs.getInt("version"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollAllowanceType> allowanceTypeMapper = (rs, rowNum) -> new PayrollAllowanceType(
            rs.getInt("id"),
            rs.getInt("company_id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getBoolean("is_taxable"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollDeductionType> deductionTypeMapper = (rs, rowNum) -> new PayrollDeductionType(
            rs.getInt("id"),
            rs.getInt("company_id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getBoolean("is_statutory"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollSalaryProfile> salaryProfileMapper = (rs, rowNum) -> new PayrollSalaryProfile(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getInt("employee_id"),
            rs.getInt("branch_id"),
            rs.getString("job_title"),
            rs.getString("salary_type"),
            rs.getBigDecimal("base_salary"),
            rs.getString("currency_code"),
            rs.getString("payroll_frequency"),
            uuid(rs, "salary_expense_account_id"),
            uuid(rs, "salary_payable_account_id"),
            rs.getBoolean("is_active"),
            rs.getDate("effective_from"),
            rs.getDate("effective_to"),
            rs.getInt("version"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollSalaryComponent> salaryComponentMapper = (rs, rowNum) -> new PayrollSalaryComponent(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getLong("salary_profile_id"),
            rs.getString("component_type"),
            (Integer) rs.getObject("allowance_type_id"),
            (Integer) rs.getObject("deduction_type_id"),
            rs.getString("calc_method"),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("percentage"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollAdjustment> adjustmentMapper = (rs, rowNum) -> new PayrollAdjustment(
            rs.getLong("id"),
            rs.getInt("company_id"),
            (Integer) rs.getObject("branch_id"),
            rs.getInt("employee_id"),
            (Long) rs.getObject("payroll_run_id"),
            rs.getString("adjustment_type"),
            rs.getString("adjustment_code"),
            rs.getString("description"),
            rs.getBigDecimal("amount"),
            rs.getDate("effective_date"),
            rs.getString("status"),
            rs.getString("approved_by"),
            rs.getTimestamp("approved_at"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollRun> runMapper = (rs, rowNum) -> new PayrollRun(
            rs.getLong("id"),
            rs.getInt("company_id"),
            (Integer) rs.getObject("branch_id"),
            rs.getString("run_label"),
            rs.getDate("period_start"),
            rs.getDate("period_end"),
            rs.getString("frequency"),
            rs.getString("currency_code"),
            rs.getString("status"),
            rs.getBigDecimal("total_gross"),
            rs.getBigDecimal("total_deductions"),
            rs.getBigDecimal("total_net"),
            rs.getInt("employee_count"),
            rs.getString("approved_by"),
            rs.getTimestamp("approved_at"),
            uuid(rs, "posting_request_id"),
            uuid(rs, "posted_journal_id"),
            rs.getTimestamp("posted_at"),
            rs.getInt("version"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollRunLine> runLineMapper = (rs, rowNum) -> new PayrollRunLine(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getLong("payroll_run_id"),
            rs.getInt("employee_id"),
            rs.getLong("salary_profile_id"),
            rs.getBigDecimal("base_salary"),
            rs.getBigDecimal("total_allowances"),
            rs.getBigDecimal("total_deductions"),
            rs.getBigDecimal("gross_salary"),
            rs.getBigDecimal("net_salary"),
            rs.getBigDecimal("paid_amount"),
            rs.getBigDecimal("remaining_amount"),
            rs.getString("payment_status"),
            rs.getInt("working_days"),
            rs.getInt("absent_days"),
            rs.getInt("late_minutes"),
            rs.getInt("overtime_minutes"),
            rs.getString("salary_type"),
            rs.getString("payroll_frequency"),
            rs.getString("currency_code"),
            rs.getString("calculation_snapshot_json"),
            rs.getString("notes"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
    );

    private final RowMapper<PayrollRunLineComponent> runLineComponentMapper = (rs, rowNum) -> new PayrollRunLineComponent(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getLong("payroll_run_line_id"),
            rs.getString("component_type"),
            (Integer) rs.getObject("type_id"),
            rs.getString("type_code"),
            rs.getString("type_name"),
            rs.getString("calc_method"),
            rs.getBigDecimal("amount"),
            rs.getString("source")
    );

    private final RowMapper<PayrollPayment> paymentMapper = (rs, rowNum) -> new PayrollPayment(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getLong("payroll_run_id"),
            rs.getDate("payment_date"),
            rs.getString("payment_method"),
            rs.getBigDecimal("total_amount"),
            rs.getString("currency_code"),
            rs.getString("reference_number"),
            rs.getString("status"),
            uuid(rs, "posting_request_id"),
            uuid(rs, "journal_id"),
            rs.getTimestamp("posted_at"),
            rs.getString("notes"),
            rs.getInt("version"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getString("created_by"),
            rs.getString("updated_by")
    );

    private final RowMapper<PayrollPaymentLine> paymentLineMapper = (rs, rowNum) -> new PayrollPaymentLine(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getLong("payroll_payment_id"),
            rs.getLong("payroll_run_line_id"),
            rs.getInt("employee_id"),
            rs.getBigDecimal("net_salary"),
            rs.getBigDecimal("paid_amount"),
            rs.getBigDecimal("remaining_amount"),
            rs.getString("payment_method"),
            rs.getString("payment_status"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
    );

    private final RowMapper<PayrollAuditLog> auditLogMapper = (rs, rowNum) -> new PayrollAuditLog(
            rs.getLong("id"),
            rs.getInt("company_id"),
            (Integer) rs.getObject("branch_id"),
            rs.getString("entity_type"),
            rs.getString("entity_id"),
            rs.getString("action"),
            rs.getString("old_value_json"),
            rs.getString("new_value_json"),
            rs.getString("performed_by"),
            rs.getTimestamp("performed_at"),
            rs.getString("remarks")
    );

    private final RowMapper<CurrentSalaryView> currentSalaryViewMapper = (rs, rowNum) -> new CurrentSalaryView(
            rs.getInt("employee_id"),
            rs.getString("employee_code"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getInt("branch_id"),
            rs.getString("job_title"),
            rs.getString("salary_type"),
            rs.getBigDecimal("base_salary"),
            rs.getString("payroll_frequency"),
            rs.getString("currency_code"),
            rs.getBigDecimal("total_allowances"),
            rs.getBigDecimal("total_deductions"),
            rs.getBigDecimal("expected_net_salary"),
            rs.getBoolean("profile_is_active"),
            rs.getDate("effective_from"),
            rs.getDate("effective_to"),
            (Long) rs.getObject("salary_profile_id"),
            rs.getString("setup_status")
    );

    public PayrollSettings getSettings(int companyId) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollSettingsTable(companyId) + " WHERE company_id = ?",
                settingsMapper,
                companyId);
    }

    public int createSettings(PayrollSettings settings) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollSettingsTable(settings.getCompanyId()) +
                " (company_id, default_currency, default_frequency, auto_include_attendance, overtime_rate_multiplier, " +
                "late_deduction_per_minute, salary_expense_account_id, salary_payable_account_id, cash_bank_account_id, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?)";
        return generatedInt(sql,
                settings.getCompanyId(),
                settings.getDefaultCurrency(),
                settings.getDefaultFrequency(),
                settings.isAutoIncludeAttendance(),
                settings.getOvertimeRateMultiplier(),
                settings.getLateDeductionPerMinute(),
                settings.getSalaryExpenseAccountId(),
                settings.getSalaryPayableAccountId(),
                settings.getCashBankAccountId(),
                settings.getCreatedBy(),
                settings.getUpdatedBy());
    }

    public int updateSettings(PayrollSettings settings) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollSettingsTable(settings.getCompanyId()) +
                " SET default_currency = ?, default_frequency = ?, auto_include_attendance = ?, overtime_rate_multiplier = ?, " +
                "late_deduction_per_minute = ?, salary_expense_account_id = CAST(? AS UUID), salary_payable_account_id = CAST(? AS UUID), cash_bank_account_id = CAST(? AS UUID), " +
                "version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND company_id = ? AND version = ?";
        return jdbcTemplate.update(sql,
                settings.getDefaultCurrency(),
                settings.getDefaultFrequency(),
                settings.isAutoIncludeAttendance(),
                settings.getOvertimeRateMultiplier(),
                settings.getLateDeductionPerMinute(),
                settings.getSalaryExpenseAccountId(),
                settings.getSalaryPayableAccountId(),
                settings.getCashBankAccountId(),
                settings.getUpdatedBy(),
                settings.getId(),
                settings.getCompanyId(),
                settings.getVersion());
    }

    public List<PayrollAllowanceType> listAllowanceTypes(int companyId, Boolean activeOnly) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.payrollAllowanceTypeTable(companyId) +
                " WHERE company_id = ?" + (Boolean.TRUE.equals(activeOnly) ? " AND is_active = TRUE" : "") + " ORDER BY name";
        return jdbcTemplate.query(sql, allowanceTypeMapper, companyId);
    }

    public PayrollAllowanceType getAllowanceType(int companyId, int id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollAllowanceTypeTable(companyId) + " WHERE company_id = ? AND id = ?",
                allowanceTypeMapper,
                companyId,
                id);
    }

    public int createAllowanceType(PayrollAllowanceType type) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollAllowanceTypeTable(type.getCompanyId()) +
                " (company_id, code, name, is_taxable, is_active, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return generatedInt(sql, type.getCompanyId(), type.getCode(), type.getName(), type.isTaxable(), type.isActive(), type.getCreatedBy(), type.getUpdatedBy());
    }

    public int updateAllowanceType(PayrollAllowanceType type) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollAllowanceTypeTable(type.getCompanyId()) +
                " SET code = ?, name = ?, is_taxable = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql, type.getCode(), type.getName(), type.isTaxable(), type.isActive(), type.getUpdatedBy(), type.getCompanyId(), type.getId());
    }

    public int deleteAllowanceType(int companyId, int id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollAllowanceTypeTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public List<PayrollDeductionType> listDeductionTypes(int companyId, Boolean activeOnly) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.payrollDeductionTypeTable(companyId) +
                " WHERE company_id = ?" + (Boolean.TRUE.equals(activeOnly) ? " AND is_active = TRUE" : "") + " ORDER BY name";
        return jdbcTemplate.query(sql, deductionTypeMapper, companyId);
    }

    public PayrollDeductionType getDeductionType(int companyId, int id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollDeductionTypeTable(companyId) + " WHERE company_id = ? AND id = ?",
                deductionTypeMapper,
                companyId,
                id);
    }

    public int createDeductionType(PayrollDeductionType type) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollDeductionTypeTable(type.getCompanyId()) +
                " (company_id, code, name, is_statutory, is_active, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return generatedInt(sql, type.getCompanyId(), type.getCode(), type.getName(), type.isStatutory(), type.isActive(), type.getCreatedBy(), type.getUpdatedBy());
    }

    public int updateDeductionType(PayrollDeductionType type) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollDeductionTypeTable(type.getCompanyId()) +
                " SET code = ?, name = ?, is_statutory = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql, type.getCode(), type.getName(), type.isStatutory(), type.isActive(), type.getUpdatedBy(), type.getCompanyId(), type.getId());
    }

    public int deleteDeductionType(int companyId, int id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollDeductionTypeTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public PayrollSalaryProfile getSalaryProfile(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollSalaryProfileTable(companyId) + " WHERE company_id = ? AND id = ?",
                salaryProfileMapper,
                companyId,
                id);
    }

    public List<PayrollSalaryProfile> listSalaryProfiles(int companyId, Integer branchId, Integer employeeId, Boolean activeOnly) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TenantSqlIdentifiers.payrollSalaryProfileTable(companyId) + " WHERE company_id = ?");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(companyId);
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (employeeId != null) {
            sql.append(" AND employee_id = ?");
            params.add(employeeId);
        }
        if (Boolean.TRUE.equals(activeOnly)) {
            sql.append(" AND is_active = TRUE");
        }
        sql.append(" ORDER BY employee_id, effective_from DESC");
        return jdbcTemplate.query(sql.toString(), salaryProfileMapper, params.toArray());
    }

    public long createSalaryProfile(PayrollSalaryProfile profile) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollSalaryProfileTable(profile.getCompanyId()) +
                " (company_id, employee_id, branch_id, job_title, salary_type, base_salary, currency_code, payroll_frequency, " +
                "salary_expense_account_id, salary_payable_account_id, is_active, effective_from, effective_to, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                profile.getCompanyId(),
                profile.getEmployeeId(),
                profile.getBranchId(),
                profile.getJobTitle(),
                profile.getSalaryType(),
                profile.getBaseSalary(),
                profile.getCurrencyCode(),
                profile.getPayrollFrequency(),
                profile.getSalaryExpenseAccountId(),
                profile.getSalaryPayableAccountId(),
                profile.isActive(),
                profile.getEffectiveFrom(),
                profile.getEffectiveTo(),
                profile.getCreatedBy(),
                profile.getUpdatedBy());
    }

    public int updateSalaryProfile(PayrollSalaryProfile profile) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollSalaryProfileTable(profile.getCompanyId()) +
                " SET branch_id = ?, job_title = ?, salary_type = ?, base_salary = ?, currency_code = ?, payroll_frequency = ?, " +
                "salary_expense_account_id = CAST(? AS UUID), salary_payable_account_id = CAST(? AS UUID), is_active = ?, effective_from = ?, effective_to = ?, " +
                "version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ? AND version = ?";
        return jdbcTemplate.update(sql,
                profile.getBranchId(),
                profile.getJobTitle(),
                profile.getSalaryType(),
                profile.getBaseSalary(),
                profile.getCurrencyCode(),
                profile.getPayrollFrequency(),
                profile.getSalaryExpenseAccountId(),
                profile.getSalaryPayableAccountId(),
                profile.isActive(),
                profile.getEffectiveFrom(),
                profile.getEffectiveTo(),
                profile.getUpdatedBy(),
                profile.getCompanyId(),
                profile.getId(),
                profile.getVersion());
    }

    public int deactivateSalaryProfile(int companyId, long id, String updatedBy) {
        return jdbcTemplate.update("UPDATE " + TenantSqlIdentifiers.payrollSalaryProfileTable(companyId) +
                " SET is_active = FALSE, version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ?",
                updatedBy,
                companyId,
                id);
    }

    public boolean hasOverlappingActiveProfile(int companyId, int employeeId, java.sql.Date from, java.sql.Date to, Long excludeProfileId) {
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.payrollSalaryProfileTable(companyId) +
                " WHERE company_id = ? AND employee_id = ? AND is_active = TRUE " +
                "AND (CAST(? AS BIGINT) IS NULL OR id <> CAST(? AS BIGINT)) " +
                "AND effective_from <= COALESCE(CAST(? AS DATE), DATE '9999-12-31') " +
                "AND COALESCE(effective_to, DATE '9999-12-31') >= CAST(? AS DATE)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, companyId, employeeId, excludeProfileId, excludeProfileId, to, from);
        return count != null && count > 0;
    }

    public int deleteSalaryProfile(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollSalaryProfileTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public List<PayrollSalaryComponent> listSalaryComponents(int companyId, long salaryProfileId, Boolean activeOnly) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.payrollSalaryComponentTable(companyId) +
                " WHERE company_id = ? AND salary_profile_id = ?" + (Boolean.TRUE.equals(activeOnly) ? " AND is_active = TRUE" : "") + " ORDER BY id";
        return jdbcTemplate.query(sql, salaryComponentMapper, companyId, salaryProfileId);
    }

    public PayrollSalaryComponent getSalaryComponent(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollSalaryComponentTable(companyId) + " WHERE company_id = ? AND id = ?",
                salaryComponentMapper,
                companyId,
                id);
    }

    public long createSalaryComponent(PayrollSalaryComponent component) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollSalaryComponentTable(component.getCompanyId()) +
                " (company_id, salary_profile_id, component_type, allowance_type_id, deduction_type_id, calc_method, amount, percentage, is_active, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                component.getCompanyId(),
                component.getSalaryProfileId(),
                component.getComponentType(),
                component.getAllowanceTypeId(),
                component.getDeductionTypeId(),
                component.getCalcMethod(),
                component.getAmount(),
                component.getPercentage(),
                component.isActive(),
                component.getCreatedBy(),
                component.getUpdatedBy());
    }

    public int updateSalaryComponent(PayrollSalaryComponent component) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollSalaryComponentTable(component.getCompanyId()) +
                " SET component_type = ?, allowance_type_id = ?, deduction_type_id = ?, calc_method = ?, amount = ?, percentage = ?, " +
                "is_active = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql,
                component.getComponentType(),
                component.getAllowanceTypeId(),
                component.getDeductionTypeId(),
                component.getCalcMethod(),
                component.getAmount(),
                component.getPercentage(),
                component.isActive(),
                component.getUpdatedBy(),
                component.getCompanyId(),
                component.getId());
    }

    public int deleteSalaryComponent(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollSalaryComponentTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public List<PayrollAdjustment> listAdjustments(int companyId, Integer branchId, Integer employeeId, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TenantSqlIdentifiers.payrollAdjustmentTable(companyId) + " WHERE company_id = ?");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(companyId);
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (employeeId != null) {
            sql.append(" AND employee_id = ?");
            params.add(employeeId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY effective_date DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), adjustmentMapper, params.toArray());
    }

    public PayrollAdjustment getAdjustment(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollAdjustmentTable(companyId) + " WHERE company_id = ? AND id = ?",
                adjustmentMapper,
                companyId,
                id);
    }

    public long createAdjustment(PayrollAdjustment adjustment) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollAdjustmentTable(adjustment.getCompanyId()) +
                " (company_id, branch_id, employee_id, payroll_run_id, adjustment_type, adjustment_code, description, amount, effective_date, status, approved_by, approved_at, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                adjustment.getCompanyId(),
                adjustment.getBranchId(),
                adjustment.getEmployeeId(),
                adjustment.getPayrollRunId(),
                adjustment.getAdjustmentType(),
                adjustment.getAdjustmentCode(),
                adjustment.getDescription(),
                adjustment.getAmount(),
                adjustment.getEffectiveDate(),
                adjustment.getStatus(),
                adjustment.getApprovedBy(),
                adjustment.getApprovedAt(),
                adjustment.getCreatedBy(),
                adjustment.getUpdatedBy());
    }

    public int updateAdjustment(PayrollAdjustment adjustment) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollAdjustmentTable(adjustment.getCompanyId()) +
                " SET branch_id = ?, payroll_run_id = ?, adjustment_type = ?, adjustment_code = ?, description = ?, amount = ?, effective_date = ?, " +
                "status = ?, approved_by = ?, approved_at = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql,
                adjustment.getBranchId(),
                adjustment.getPayrollRunId(),
                adjustment.getAdjustmentType(),
                adjustment.getAdjustmentCode(),
                adjustment.getDescription(),
                adjustment.getAmount(),
                adjustment.getEffectiveDate(),
                adjustment.getStatus(),
                adjustment.getApprovedBy(),
                adjustment.getApprovedAt(),
                adjustment.getUpdatedBy(),
                adjustment.getCompanyId(),
                adjustment.getId());
    }

    public int deleteAdjustment(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollAdjustmentTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public PayrollRun getRun(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollRunTable(companyId) + " WHERE company_id = ? AND id = ?",
                runMapper,
                companyId,
                id);
    }

    public List<PayrollRun> listRuns(int companyId, Integer branchId, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TenantSqlIdentifiers.payrollRunTable(companyId) + " WHERE company_id = ?");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(companyId);
        if (branchId != null) {
            sql.append(" AND branch_id = ?");
            params.add(branchId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY period_start DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), runMapper, params.toArray());
    }

    public long createRun(PayrollRun run) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollRunTable(run.getCompanyId()) +
                " (company_id, branch_id, run_label, period_start, period_end, frequency, currency_code, status, total_gross, total_deductions, total_net, " +
                "employee_count, approved_by, approved_at, posting_request_id, posted_journal_id, posted_at, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?)";
        return generatedLong(sql,
                run.getCompanyId(),
                run.getBranchId(),
                run.getRunLabel(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getFrequency(),
                run.getCurrencyCode(),
                run.getStatus(),
                run.getTotalGross(),
                run.getTotalDeductions(),
                run.getTotalNet(),
                run.getEmployeeCount(),
                run.getApprovedBy(),
                run.getApprovedAt(),
                run.getPostingRequestId(),
                run.getPostedJournalId(),
                run.getPostedAt(),
                run.getCreatedBy(),
                run.getUpdatedBy());
    }

    public int updateRun(PayrollRun run) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollRunTable(run.getCompanyId()) +
                " SET branch_id = ?, run_label = ?, period_start = ?, period_end = ?, frequency = ?, currency_code = ?, status = ?, total_gross = ?, " +
                "total_deductions = ?, total_net = ?, employee_count = ?, approved_by = ?, approved_at = ?, posting_request_id = CAST(? AS UUID), posted_journal_id = CAST(? AS UUID), posted_at = ?, " +
                "version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ? AND version = ?";
        return jdbcTemplate.update(sql,
                run.getBranchId(),
                run.getRunLabel(),
                run.getPeriodStart(),
                run.getPeriodEnd(),
                run.getFrequency(),
                run.getCurrencyCode(),
                run.getStatus(),
                run.getTotalGross(),
                run.getTotalDeductions(),
                run.getTotalNet(),
                run.getEmployeeCount(),
                run.getApprovedBy(),
                run.getApprovedAt(),
                run.getPostingRequestId(),
                run.getPostedJournalId(),
                run.getPostedAt(),
                run.getUpdatedBy(),
                run.getCompanyId(),
                run.getId(),
                run.getVersion());
    }

    public int deleteRun(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollRunTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public PayrollRunLine getRunLine(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollRunLineTable(companyId) + " WHERE company_id = ? AND id = ?",
                runLineMapper,
                companyId,
                id);
    }

    public List<PayrollRunLine> listRunLines(int companyId, long payrollRunId) {
        return jdbcTemplate.query("SELECT * FROM " + TenantSqlIdentifiers.payrollRunLineTable(companyId) + " WHERE company_id = ? AND payroll_run_id = ? ORDER BY employee_id",
                runLineMapper,
                companyId,
                payrollRunId);
    }

    public long createRunLine(PayrollRunLine line) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollRunLineTable(line.getCompanyId()) +
                " (company_id, payroll_run_id, employee_id, salary_profile_id, base_salary, total_allowances, total_deductions, gross_salary, net_salary, " +
                "paid_amount, remaining_amount, payment_status, working_days, absent_days, late_minutes, overtime_minutes, salary_type, payroll_frequency, currency_code, calculation_snapshot_json, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                line.getCompanyId(),
                line.getPayrollRunId(),
                line.getEmployeeId(),
                line.getSalaryProfileId(),
                line.getBaseSalary(),
                line.getTotalAllowances(),
                line.getTotalDeductions(),
                line.getGrossSalary(),
                line.getNetSalary(),
                line.getPaidAmount(),
                line.getRemainingAmount(),
                line.getPaymentStatus(),
                line.getWorkingDays(),
                line.getAbsentDays(),
                line.getLateMinutes(),
                line.getOvertimeMinutes(),
                line.getSalaryType(),
                line.getPayrollFrequency(),
                line.getCurrencyCode(),
                line.getCalculationSnapshotJson(),
                line.getNotes());
    }

    public int updateRunLine(PayrollRunLine line) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollRunLineTable(line.getCompanyId()) +
                " SET base_salary = ?, total_allowances = ?, total_deductions = ?, gross_salary = ?, net_salary = ?, paid_amount = ?, remaining_amount = ?, " +
                "payment_status = ?, working_days = ?, absent_days = ?, late_minutes = ?, overtime_minutes = ?, salary_type = ?, payroll_frequency = ?, currency_code = ?, " +
                "calculation_snapshot_json = ?, notes = ?, updated_at = CURRENT_TIMESTAMP WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql,
                line.getBaseSalary(),
                line.getTotalAllowances(),
                line.getTotalDeductions(),
                line.getGrossSalary(),
                line.getNetSalary(),
                line.getPaidAmount(),
                line.getRemainingAmount(),
                line.getPaymentStatus(),
                line.getWorkingDays(),
                line.getAbsentDays(),
                line.getLateMinutes(),
                line.getOvertimeMinutes(),
                line.getSalaryType(),
                line.getPayrollFrequency(),
                line.getCurrencyCode(),
                line.getCalculationSnapshotJson(),
                line.getNotes(),
                line.getCompanyId(),
                line.getId());
    }

    public int deleteRunLine(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollRunLineTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public int deleteRunLinesByRun(int companyId, long payrollRunId) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollRunLineTable(companyId) +
                " WHERE company_id = ? AND payroll_run_id = ?", companyId, payrollRunId);
    }

    public PayrollRunLineComponent getRunLineComponent(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollRunLineComponentTable(companyId) + " WHERE company_id = ? AND id = ?",
                runLineComponentMapper,
                companyId,
                id);
    }

    public List<PayrollRunLineComponent> listRunLineComponents(int companyId, long runLineId) {
        return jdbcTemplate.query("SELECT * FROM " + TenantSqlIdentifiers.payrollRunLineComponentTable(companyId) + " WHERE company_id = ? AND payroll_run_line_id = ? ORDER BY id",
                runLineComponentMapper,
                companyId,
                runLineId);
    }

    public long createRunLineComponent(PayrollRunLineComponent component) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollRunLineComponentTable(component.getCompanyId()) +
                " (company_id, payroll_run_line_id, component_type, type_id, type_code, type_name, calc_method, amount, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                component.getCompanyId(),
                component.getPayrollRunLineId(),
                component.getComponentType(),
                component.getTypeId(),
                component.getTypeCode(),
                component.getTypeName(),
                component.getCalcMethod(),
                component.getAmount(),
                component.getSource());
    }

    public int updateRunLineComponent(PayrollRunLineComponent component) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollRunLineComponentTable(component.getCompanyId()) +
                " SET component_type = ?, type_id = ?, type_code = ?, type_name = ?, calc_method = ?, amount = ?, source = ? WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql,
                component.getComponentType(),
                component.getTypeId(),
                component.getTypeCode(),
                component.getTypeName(),
                component.getCalcMethod(),
                component.getAmount(),
                component.getSource(),
                component.getCompanyId(),
                component.getId());
    }

    public int deleteRunLineComponent(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollRunLineComponentTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public PayrollPayment getPayment(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollPaymentTable(companyId) + " WHERE company_id = ? AND id = ?",
                paymentMapper,
                companyId,
                id);
    }

    public List<PayrollPayment> listPayments(int companyId, Long payrollRunId) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.payrollPaymentTable(companyId) + " WHERE company_id = ?" +
                (payrollRunId == null ? "" : " AND payroll_run_id = ?") + " ORDER BY payment_date DESC, id DESC";
        return payrollRunId == null
                ? jdbcTemplate.query(sql, paymentMapper, companyId)
                : jdbcTemplate.query(sql, paymentMapper, companyId, payrollRunId);
    }

    public long createPayment(PayrollPayment payment) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollPaymentTable(payment.getCompanyId()) +
                " (company_id, payroll_run_id, payment_date, payment_method, total_amount, currency_code, reference_number, status, posting_request_id, journal_id, posted_at, notes, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?)";
        return generatedLong(sql,
                payment.getCompanyId(),
                payment.getPayrollRunId(),
                payment.getPaymentDate(),
                payment.getPaymentMethod(),
                payment.getTotalAmount(),
                payment.getCurrencyCode(),
                payment.getReferenceNumber(),
                payment.getStatus(),
                payment.getPostingRequestId(),
                payment.getJournalId(),
                payment.getPostedAt(),
                payment.getNotes(),
                payment.getCreatedBy(),
                payment.getUpdatedBy());
    }

    public int updatePayment(PayrollPayment payment) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollPaymentTable(payment.getCompanyId()) +
                " SET payment_date = ?, payment_method = ?, total_amount = ?, currency_code = ?, reference_number = ?, status = ?, posting_request_id = CAST(? AS UUID), journal_id = CAST(? AS UUID), " +
                "posted_at = ?, notes = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE company_id = ? AND id = ? AND version = ?";
        return jdbcTemplate.update(sql,
                payment.getPaymentDate(),
                payment.getPaymentMethod(),
                payment.getTotalAmount(),
                payment.getCurrencyCode(),
                payment.getReferenceNumber(),
                payment.getStatus(),
                payment.getPostingRequestId(),
                payment.getJournalId(),
                payment.getPostedAt(),
                payment.getNotes(),
                payment.getUpdatedBy(),
                payment.getCompanyId(),
                payment.getId(),
                payment.getVersion());
    }

    public int deletePayment(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollPaymentTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public PayrollPaymentLine getPaymentLine(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollPaymentLineTable(companyId) + " WHERE company_id = ? AND id = ?",
                paymentLineMapper,
                companyId,
                id);
    }

    public List<PayrollPaymentLine> listPaymentLines(int companyId, long payrollPaymentId) {
        return jdbcTemplate.query("SELECT * FROM " + TenantSqlIdentifiers.payrollPaymentLineTable(companyId) + " WHERE company_id = ? AND payroll_payment_id = ? ORDER BY employee_id",
                paymentLineMapper,
                companyId,
                payrollPaymentId);
    }

    public long createPaymentLine(PayrollPaymentLine line) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollPaymentLineTable(line.getCompanyId()) +
                " (company_id, payroll_payment_id, payroll_run_line_id, employee_id, net_salary, paid_amount, remaining_amount, payment_method, payment_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                line.getCompanyId(),
                line.getPayrollPaymentId(),
                line.getPayrollRunLineId(),
                line.getEmployeeId(),
                line.getNetSalary(),
                line.getPaidAmount(),
                line.getRemainingAmount(),
                line.getPaymentMethod(),
                line.getPaymentStatus());
    }

    public int updatePaymentLine(PayrollPaymentLine line) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollPaymentLineTable(line.getCompanyId()) +
                " SET net_salary = ?, paid_amount = ?, remaining_amount = ?, payment_method = ?, payment_status = ?, updated_at = CURRENT_TIMESTAMP WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql,
                line.getNetSalary(),
                line.getPaidAmount(),
                line.getRemainingAmount(),
                line.getPaymentMethod(),
                line.getPaymentStatus(),
                line.getCompanyId(),
                line.getId());
    }

    public int deletePaymentLine(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollPaymentLineTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public PayrollAuditLog getAuditLog(int companyId, long id) {
        return queryOne("SELECT * FROM " + TenantSqlIdentifiers.payrollAuditLogTable(companyId) + " WHERE company_id = ? AND id = ?",
                auditLogMapper,
                companyId,
                id);
    }

    public List<PayrollAuditLog> listAuditLogs(int companyId, String entityType, String entityId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TenantSqlIdentifiers.payrollAuditLogTable(companyId) + " WHERE company_id = ?");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(companyId);
        if (entityType != null) {
            sql.append(" AND entity_type = ?");
            params.add(entityType);
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            params.add(entityId);
        }
        sql.append(" ORDER BY performed_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), auditLogMapper, params.toArray());
    }

    public long createAuditLog(PayrollAuditLog log) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.payrollAuditLogTable(log.getCompanyId()) +
                " (company_id, branch_id, entity_type, entity_id, action, old_value_json, new_value_json, performed_by, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return generatedLong(sql,
                log.getCompanyId(),
                log.getBranchId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getOldValueJson(),
                log.getNewValueJson(),
                log.getPerformedBy(),
                log.getRemarks());
    }

    public int updateAuditLog(PayrollAuditLog log) {
        String sql = "UPDATE " + TenantSqlIdentifiers.payrollAuditLogTable(log.getCompanyId()) +
                " SET branch_id = ?, entity_type = ?, entity_id = ?, action = ?, old_value_json = ?, new_value_json = ?, performed_by = ?, remarks = ? WHERE company_id = ? AND id = ?";
        return jdbcTemplate.update(sql,
                log.getBranchId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction(),
                log.getOldValueJson(),
                log.getNewValueJson(),
                log.getPerformedBy(),
                log.getRemarks(),
                log.getCompanyId(),
                log.getId());
    }

    public int deleteAuditLog(int companyId, long id) {
        return jdbcTemplate.update("DELETE FROM " + TenantSqlIdentifiers.payrollAuditLogTable(companyId) + " WHERE company_id = ? AND id = ?", companyId, id);
    }

    public List<CurrentSalaryView> listCurrentSalaries(int companyId, Integer branchId, String setupStatus) {
        StringBuilder sql = new StringBuilder(
                "WITH component_totals AS (" +
                        "SELECT salary_profile_id, " +
                        "COALESCE(SUM(CASE WHEN component_type = 'ALLOWANCE' AND is_active THEN amount ELSE 0 END), 0) AS total_allowances, " +
                        "COALESCE(SUM(CASE WHEN component_type = 'DEDUCTION' AND is_active THEN amount ELSE 0 END), 0) AS total_deductions " +
                        "FROM " + TenantSqlIdentifiers.payrollSalaryComponentTable(companyId) + " WHERE company_id = ? GROUP BY salary_profile_id), " +
                        "active_profile AS (" +
                        "SELECT DISTINCT ON (employee_id) * FROM " + TenantSqlIdentifiers.payrollSalaryProfileTable(companyId) +
                        " WHERE company_id = ? AND is_active = TRUE AND effective_from <= CURRENT_DATE " +
                        "AND (effective_to IS NULL OR effective_to >= CURRENT_DATE) ORDER BY employee_id, effective_from DESC, id DESC) " +
                        "SELECT e.id AS employee_id, e.employee_code, e.first_name, e.last_name, e.branch_id, p.job_title, p.salary_type, " +
                        "COALESCE(p.base_salary, 0) AS base_salary, p.payroll_frequency, p.currency_code, " +
                        "COALESCE(ct.total_allowances, 0) AS total_allowances, COALESCE(ct.total_deductions, 0) AS total_deductions, " +
                        "COALESCE(p.base_salary, 0) + COALESCE(ct.total_allowances, 0) - COALESCE(ct.total_deductions, 0) AS expected_net_salary, " +
                        "COALESCE(p.is_active, FALSE) AS profile_is_active, p.effective_from, p.effective_to, p.id AS salary_profile_id, " +
                        "CASE WHEN p.id IS NULL THEN 'MISSING' ELSE 'CONFIGURED' END AS setup_status " +
                        "FROM " + TenantSqlIdentifiers.hrEmployeeTable(companyId) + " e " +
                        "LEFT JOIN active_profile p ON p.employee_id = e.id " +
                        "LEFT JOIN component_totals ct ON ct.salary_profile_id = p.id " +
                        "WHERE e.company_id = ? AND e.is_active = TRUE");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(companyId);
        params.add(companyId);
        params.add(companyId);
        if (branchId != null) {
            sql.append(" AND e.branch_id = ?");
            params.add(branchId);
        }
        if (setupStatus != null) {
            sql.append(" AND CASE WHEN p.id IS NULL THEN 'MISSING' ELSE 'CONFIGURED' END = ?");
            params.add(setupStatus);
        }
        sql.append(" ORDER BY e.branch_id, e.employee_code");
        return jdbcTemplate.query(sql.toString(), currentSalaryViewMapper, params.toArray());
    }

    private UUID uuid(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value == null ? null : (UUID) value;
    }

    private <T> T queryOne(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, mapper, args);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private int generatedInt(String sql, Object... args) {
        return generatedKey(sql, args).intValue();
    }

    private long generatedLong(String sql, Object... args) {
        return generatedKey(sql, args).longValue();
    }

    private Number generatedKey(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Object id = keys.get("id");
            if (id == null) {
                id = keys.get("ID");
            }
            if (id instanceof Number number) {
                return number;
            }
            // Fallback to first column if 'id' not found by name
            if (!keys.isEmpty()) {
                Object firstValue = keys.values().iterator().next();
                if (firstValue instanceof Number number) {
                    return number;
                }
            }
        }
        throw new IllegalStateException("Insert did not return a generated id");
    }
}
