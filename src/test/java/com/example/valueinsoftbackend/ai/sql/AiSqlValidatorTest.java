package com.example.valueinsoftbackend.ai.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSqlValidatorTest {

    private final AiSqlValidator validator = new AiSqlValidator(new AiSqlSchemaCatalog());

    @Test
    void acceptsApprovedAggregateInventorySelect() {
        assertDoesNotThrow(() -> validator.validate("""
                select count(distinct p.product_id) as product_count
                from c_7.inventory_product p
                join c_7.inventory_branch_stock_balance st on st.product_id = p.product_id
                where st.branch_id = :branchId and st.quantity > 0
                """, 7, 3L));
    }

    @Test
    void rejectsNonSelectStatements() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate(
                "update c_7.inventory_product set product_name = 'x'",
                7,
                3L
        ));
    }

    @Test
    void rejectsUnapprovedTables() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate(
                "select * from public.users limit 10",
                7,
                3L
        ));
    }

    @Test
    void rejectsStockQueriesWithoutBranchParameter() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate("""
                select count(*)
                from c_7.inventory_branch_stock_balance st
                where st.quantity > 0
                """, 7, 3L));
    }

    @Test
    void acceptsSelectedBranchDynamicSalesTable() {
        assertDoesNotThrow(() -> validator.validate("""
                select count(*) as order_count
                from c_7."PosOrder_3" o
                where o."orderTime" >= current_date
                """, 7, 3L));
    }

    @Test
    void rejectsDifferentBranchDynamicSalesTable() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate("""
                select count(*) as order_count
                from c_7."PosOrder_4" o
                where o."orderTime" >= current_date
                """, 7, 3L));
    }

    @Test
    void acceptsCustomerQueryWithBranchFilter() {
        assertDoesNotThrow(() -> validator.validate("""
                select c.c_id, c."clientName"
                from c_7."Client" c
                where c."branchId" = :branchId
                limit 20
                """, 7, 3L));
    }

    @Test
    void rejectsCustomerQueryWithoutBranchFilter() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate("""
                select c.c_id, c."clientName"
                from c_7."Client" c
                limit 20
                """, 7, 3L));
    }

    @Test
    void acceptsPublicFinanceQueryWithCompanyFilter() {
        assertDoesNotThrow(() -> validator.validate("""
                select a.account_id, a.account_code, a.account_name
                from public.finance_account a
                where a.company_id = :companyId
                limit 20
                """, 7, 3L));
    }

    @Test
    void rejectsPublicFinanceQueryWithoutCompanyFilter() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate("""
                select a.account_id, a.account_code, a.account_name
                from public.finance_account a
                limit 20
                """, 7, 3L));
    }

    @Test
    void rejectsWildcardSelects() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate(
                "select * from c_7.inventory_product p limit 10",
                7,
                3L
        ));
    }

    @Test
    void rejectsSensitiveUserColumns() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate("""
                select u.id, u."userPassword"
                from c_7."users" u
                where u."branchId" = :branchId
                limit 10
                """, 7, 3L));
    }

    @Test
    void rejectsUnboundedListQueries() {
        assertThrows(AiSqlValidationException.class, () -> validator.validate(
                "select p.product_id, p.product_name from c_7.inventory_product p",
                7,
                3L
        ));
    }
}
