package com.example.valueinsoftbackend.util;

import java.util.regex.Pattern;

public final class TenantSqlIdentifiers {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private TenantSqlIdentifiers() {
    }

    public static String requireSchemaName(String schemaName) {
        requireIdentifier(schemaName, "schemaName");
        return schemaName;
    }

    public static String companySchema(int companyId) {
        requirePositive(companyId, "companyId");
        return "c_" + companyId;
    }

    public static String expensesTable(int companyId, boolean isStatic) {
        return companySchema(companyId) + ".\"" + (isStatic ? "ExpensesStatic" : "Expenses") + "\"";
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

    public static String shiftPeriodTable(int companyId) {
        return companySchema(companyId) + ".\"PosShiftPeriod\"";
    }

    public static String supplierReceiptsTable(int companyId) {
        return companySchema(companyId) + ".\"supplierReciepts\"";
    }

    public static String supplierBoughtProductTable(int companyId) {
        return companySchema(companyId) + ".\"SupplierBProduct\"";
    }

    public static String companySubscriptionTable() {
        return "public.\"CompanySubscription\"";
    }

    public static String supplierTable(int companyId, int branchId) {
        requirePositive(branchId, "branchId");
        return companySchema(companyId) + ".supplier_" + branchId;
    }

    public static void requirePositive(int value, String fieldName) {
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
