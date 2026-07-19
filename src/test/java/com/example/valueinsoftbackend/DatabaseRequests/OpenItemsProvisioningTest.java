package com.example.valueinsoftbackend.DatabaseRequests;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpenItemsProvisioningTest {

    @Test
    void companyProvisioningInvokesEveryOpenItemsEnsureFunction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DbBranch dbBranch = mock(DbBranch.class);
        DbCompany dbCompany = new DbCompany(
                jdbcTemplate, namedJdbcTemplate, dbBranch, "postgres", false);

        assertTrue(dbCompany.createCompanySchema(42));

        verify(jdbcTemplate).execute(
                "SELECT public.ensure_ar_open_items_foundation_for_tenant('c_42', 42)");
        verify(jdbcTemplate).execute(
                "SELECT public.ensure_ap_open_items_foundation_for_tenant('c_42', 42)");
        verify(jdbcTemplate).execute(
                "SELECT public.ensure_receipt_hardening_for_tenant('c_42', 42)");
        verify(jdbcTemplate).execute(
                "SELECT public.ensure_credit_debit_notes_for_tenant('c_42', 42)");
        verify(jdbcTemplate).execute(
                "SELECT public.ensure_inventory_workspace_receipt_foundation_for_tenant('c_42', 42)");
    }

    @Test
    void branchSupplierProvisioningIncludesPaymentTerms() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DbBranch dbBranch = new DbBranch(jdbcTemplate);

        assertTrue(dbBranch.createSupplierTable(7, 42));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertTrue(sql.getValue().contains("payment_terms_days INTEGER NOT NULL DEFAULT 0"));
    }
}
