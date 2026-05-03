package com.example.valueinsoftbackend.util;

import java.util.regex.Pattern;

public final class TenantSqlIdentifiers {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private TenantSqlIdentifiers() {
    }

    public static String offerTable(int companyId) {
        return companySchema(companyId) + ".pos_offers";
    }

    public static String requireSchemaName(String schemaName) {
        requireIdentifier(schemaName, "schemaName");
        return schemaName;
    }

    public static String companySchema(int companyId) {
        requirePositive(companyId, "companyId");
        return "c_" + companyId;
    }

    public static String companySchema(long companyId) {
        requirePositive(companyId, "companyId");
        return "c_" + companyId;
    }

    public static String expensesTable(int companyId, boolean isStatic) {
        return companySchema(companyId) + "." + (isStatic ? "\"ExpensesStatic\"" : "\"Expenses\"");
    }

    public static String expensesStaticHistoryTable(int companyId) {
        return companySchema(companyId) + ".\"ExpensesStaticHistory\"";
    }

    public static String inventoryTransactionsTable(int companyId, int branchId) {
        requirePositive(branchId, "branchId");
        return companySchema(companyId) + ".\"InventoryTransactions_" + branchId + "\"";
    }

    public static String orderTable(int companyId, int branchId) {
        requirePositive(branchId, "branchId");
        return companySchema(companyId) + ".\"PosOrder_" + branchId + "\"";
    }

    public static String orderDetailTable(int companyId, int branchId) {
        requirePositive(branchId, "branchId");
        return companySchema(companyId) + ".\"PosOrderDetail_" + branchId + "\"";
    }

    public static String productTable(int companyId, int branchId) {
        requirePositive(branchId, "branchId");
        return companySchema(companyId) + ".\"PosProduct_" + branchId + "\"";
    }

    public static String inventoryProductTable(int companyId) {
        return companySchema(companyId) + ".inventory_product";
    }

    public static String inventoryProductTable(long companyId) {
        return companySchema(companyId) + ".inventory_product";
    }

    public static String inventoryBranchStockBalanceTable(int companyId) {
        return companySchema(companyId) + ".inventory_branch_stock_balance";
    }

    public static String inventoryBranchStockBalanceTable(long companyId) {
        return companySchema(companyId) + ".inventory_branch_stock_balance";
    }

    public static String inventoryStockLedgerTable(int companyId) {
        return companySchema(companyId) + ".inventory_stock_ledger";
    }

    public static String inventoryLegacyProductMappingTable(int companyId) {
        return companySchema(companyId) + ".inventory_legacy_product_mapping";
    }

    public static String inventoryProductTemplateTable(int companyId) {
        return companySchema(companyId) + ".inventory_product_template";
    }

    public static String inventoryPricingPolicyTable(long companyId) {
        return companySchema(companyId) + ".inventory_pricing_policy";
    }

    public static String inventoryAttributeDefinitionTable(int companyId) {
        return companySchema(companyId) + ".inventory_attribute_definition";
    }

    public static String inventoryTemplateAttributeTable(int companyId) {
        return companySchema(companyId) + ".inventory_template_attribute";
    }

    public static String inventoryProductAttributeValueTable(int companyId) {
        return companySchema(companyId) + ".inventory_product_attribute_value";
    }

    public static String shiftPeriodTable(int companyId) {
        return companySchema(companyId) + ".\"PosShiftPeriod\"";
    }

    public static String shiftEventTable(int companyId) {
        return companySchema(companyId) + ".shift_event";
    }

    public static String shiftCashMovementTable(int companyId) {
        return companySchema(companyId) + ".shift_cash_movement";
    }

    public static String supplierReceiptsTable(int companyId) {
        return companySchema(companyId) + ".\"supplierReciepts\"";
    }

    public static String supplierBoughtProductTable(int companyId) {
        return companySchema(companyId) + ".\"SupplierBProduct\"";
    }

    public static String damagedListTable(int companyId) {
        return companySchema(companyId) + ".\"DamagedList\"";
    }

    public static String fixAreaTable(int companyId) {
        return companySchema(companyId) + ".\"FixArea\"";
    }

    public static String clientTable(int companyId) {
        return companySchema(companyId) + ".\"Client\"";
    }

    public static String clientReceiptsTable(int companyId) {
        return companySchema(companyId) + ".\"ClientReceipts\"";
    }

    public static String userTable(int companyId) {
        return companySchema(companyId) + ".\"users\"";
    }

    public static String categoryJsonTable(int companyId) {
        return companySchema(companyId) + ".\"PosCateJson\"";
    }

    public static String mainMajorTable(int companyId) {
        return companySchema(companyId) + ".\"MainMajor\"";
    }

    public static String companyAnalysisTable(int companyId) {
        return companySchema(companyId) + ".\"CompanyAnalysis\"";
    }

    public static String hrEmployeeTable(int companyId) {
        return companySchema(companyId) + ".hr_employee";
    }

    public static String hrShiftTable(int companyId) {
        return companySchema(companyId) + ".hr_shift";
    }

    public static String hrEmployeeShiftTable(int companyId) {
        return companySchema(companyId) + ".hr_employee_shift";
    }

    public static String hrAttendanceLogTable(int companyId) {
        return companySchema(companyId) + ".hr_attendance_log";
    }

    public static String hrAttendanceDayTable(int companyId) {
        return companySchema(companyId) + ".hr_attendance_day";
    }

    public static String companySubscriptionTable() {
        return "public.\"CompanySubscription\"";
    }

    public static String supplierTable(int companyId, int branchId) {
        requirePositive(branchId, "branchId");
        return companySchema(companyId) + ".supplier_" + branchId;
    }

    // -------------------------------------------------------
    // Payroll module tables
    // -------------------------------------------------------

    public static String payrollSettingsTable(int companyId) {
        return companySchema(companyId) + ".payroll_settings";
    }

    public static String payrollAllowanceTypeTable(int companyId) {
        return companySchema(companyId) + ".payroll_allowance_type";
    }

    public static String payrollDeductionTypeTable(int companyId) {
        return companySchema(companyId) + ".payroll_deduction_type";
    }

    public static String payrollSalaryProfileTable(int companyId) {
        return companySchema(companyId) + ".payroll_salary_profile";
    }

    public static String payrollSalaryComponentTable(int companyId) {
        return companySchema(companyId) + ".payroll_salary_component";
    }

    public static String payrollAdjustmentTable(int companyId) {
        return companySchema(companyId) + ".payroll_adjustment";
    }

    public static String payrollRunTable(int companyId) {
        return companySchema(companyId) + ".payroll_run";
    }

    public static String payrollRunLineTable(int companyId) {
        return companySchema(companyId) + ".payroll_run_line";
    }

    public static String payrollRunLineComponentTable(int companyId) {
        return companySchema(companyId) + ".payroll_run_line_component";
    }

    public static String payrollPaymentTable(int companyId) {
        return companySchema(companyId) + ".payroll_payment";
    }

    public static String payrollPaymentLineTable(int companyId) {
        return companySchema(companyId) + ".payroll_payment_line";
    }

    public static String payrollAuditLogTable(int companyId) {
        return companySchema(companyId) + ".payroll_audit_log";
    }

    // -------------------------------------------------------
    // POS Offline Sync tables (tenant schema)
    // -------------------------------------------------------

    public static String posDeviceTable(long companyId) {
        return companySchema(companyId) + ".pos_device";
    }

    public static String posSyncBatchTable(long companyId) {
        return companySchema(companyId) + ".pos_sync_batch";
    }

    public static String posOfflineOrderImportTable(long companyId) {
        return companySchema(companyId) + ".pos_offline_order_import";
    }

    public static String posIdempotencyKeyTable(long companyId) {
        return companySchema(companyId) + ".pos_idempotency_key";
    }

    public static String posOfflineOrderErrorTable(long companyId) {
        return companySchema(companyId) + ".pos_offline_order_error";
    }

    public static String posBootstrapVersionTable(long companyId) {
        return companySchema(companyId) + ".pos_bootstrap_version";
    }

    public static String posDeviceSessionTable(long companyId) {
        return companySchema(companyId) + ".pos_device_session";
    }

    public static String posSyncAuditLogTable(long companyId) {
        return companySchema(companyId) + ".pos_sync_audit_log";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posDeviceTable() {
        return "public.pos_device";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posSyncBatchTable() {
        return "public.pos_sync_batch";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posOfflineOrderImportTable() {
        return "public.pos_offline_order_import";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posIdempotencyKeyTable() {
        return "public.pos_idempotency_key";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posOfflineOrderErrorTable() {
        return "public.pos_offline_order_error";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posBootstrapVersionTable() {
        return "public.pos_bootstrap_version";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posDeviceSessionTable() {
        return "public.pos_device_session";
    }

    /**
     * @deprecated Public offline sync tables are compatibility artifacts from V74/V75.
     * Runtime offline sync code must use tenant-scoped methods with companyId.
     */
    @Deprecated(forRemoval = false)
    public static String posSyncAuditLogTable() {
        return "public.pos_sync_audit_log";
    }

    // -------------------------------------------------------

    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    public static void requireIdentifier(String value, String fieldName) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
    }
}
