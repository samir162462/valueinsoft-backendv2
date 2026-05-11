package com.example.valueinsoftbackend.ai.sql;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class AiSqlSchemaCatalog {

    public String promptCatalog(long companyId, Long branchId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        String dynamicBranchTables = branchId == null
                ? "No branch-specific dynamic tables are available because no branch is selected."
                : """
                Selected branch dynamic tenant tables:
                - %s op: branch product snapshot/legacy stock. Use for old product rows in this branch.
                - %s o: sales/POS orders. Use for sales totals, cashiers, order counts, order dates, client order history.
                - %s od: POS order lines. Use for sold items, quantities, line totals, product sales ranking.
                - %s it: legacy inventory transactions. Use for stock purchase/return/payment transactions.
                - %s sl: branch suppliers. Use for supplier names, phones, balances, status, total purchases.
                """.formatted(
                        TenantSqlIdentifiers.productTable(Math.toIntExact(companyId), Math.toIntExact(branchId)),
                        TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)),
                        TenantSqlIdentifiers.orderDetailTable(Math.toIntExact(companyId), Math.toIntExact(branchId)),
                        TenantSqlIdentifiers.inventoryTransactionsTable(Math.toIntExact(companyId), Math.toIntExact(branchId)),
                        TenantSqlIdentifiers.supplierTable(Math.toIntExact(companyId), Math.toIntExact(branchId))
                );

        return """
                Approved read-only SQL schema for this company.

                Tenant inventory:
                - %s.inventory_product p: main product catalog; product names, serial/barcode, category, prices, supplier id, product state, UOM, pricing policy.
                - %s.inventory_branch_stock_balance st: branch stock balances; branch_id, product_id, quantity, reserved_qty. Filter st.branch_id = :branchId.
                - %s.inventory_stock_ledger l: stock movements; branch_id, product_id, quantity_delta, movement_type, supplier_id, totals, payment type, created_at. Filter l.branch_id = :branchId.
                - %s.inventory_legacy_product_mapping lm: links old branch product ids to modern inventory_product ids.
                - %s.inventory_product_template pt: product templates/business line setup.
                - %s.inventory_template_attribute ta: template-to-attribute mapping.
                - %s.inventory_attribute_definition ad: dynamic product attribute definitions.
                - %s.inventory_product_attribute_value pav: dynamic product attribute values.
                - %s.inventory_uom_dimension ud: inventory unit dimensions.
                - %s.inventory_uom_unit uom: inventory units of measure.
                - %s.inventory_uom_conversion uc: unit conversion multipliers.
                - %s.inventory_pricing_policy pp: product pricing policies.
                - %s.inventory_import_batch ib: bulk import batches and status.
                - %s.inventory_import_row ir: bulk import row data and import actions.
                - %s.inventory_import_error ie: bulk import validation/import errors.
                - %s.inventory_import_audit_log ial: bulk import audit events.
                - %s.inventory_presets ipr: inventory preset/configuration rows if present.
                - %s.inventory_business_line ibl: inventory business line definitions if present.

                Tenant customers, suppliers, sales, POS, shifts:
                - %s."Branch" tb: tenant branch metadata if present. Use public."Branch" for canonical branch data.
                - %s."PosProduct" legacy_pp, %s."InventoryTransactions" legacy_it, %s."PosOrder" legacy_o, %s."PosOrderDetail" legacy_od: legacy unsuffixed POS tables if present; prefer selected branch dynamic tables below.
                - %s."Client" c: customers; name, phone, gender, description, branchId, registeredTime. Filter c."branchId" = :branchId.
                - %s."ClientReceipts" cr: customer payments/receipts. Filter cr."branchId" = :branchId.
                - %s."supplierReciepts" sr: supplier payments/receipts. Filter sr."branchId" = :branchId.
                - %s."SupplierBProduct" sbp: supplier purchased products. Filter sbp."branchId" = :branchId.
                - %s."supplier " slegacy: legacy supplier master table if present.
                - %s."DamagedList" d: damaged products. Filter d."branchId" = :branchId.
                - %s."FixArea" fa: repair/fix jobs. Filter fa."branchId" = :branchId when present.
                - %s.fix_area_parts fap: repair parts used on fix jobs if present.
                - %s."Expenses" e: expenses. Filter e."branchId" = :branchId.
                - %s."ExpensesStatic" es: recurring/static expenses. Filter es."branchId" = :branchId when present.
                - %s."ExpensesStaticHistory" esh: recurring expense history. Filter esh."branchId" = :branchId when present.
                - %s."CompanyAnalysis" ca: daily branch metrics. Filter ca."branchId" = :branchId.
                - %s."MainMajor" mm: major/category reference data.
                - %s."PosCateJson" pcj: POS category JSON by branch. Filter pcj."BranchId" = :branchId.
                - %s."PosShiftPeriod" sp: shifts; open/closed status, cash, sales totals. Filter sp."branchId" = :branchId.
                - %s.shift_event se: shift events. Filter se.branch_id = :branchId.
                - %s.shift_cash_movement scm: shift cash movements. Filter scm.branch_id = :branchId.
                - %s.pos_offers po: POS offers/discounts for the tenant if present.

                Tenant users, HR, payroll:
                - %s."users" u: company users; select id, userName, userEmail, userRole, userPhone, branchId, firstName, lastName, gender, creationTime, salaryPHour only. Never select userPassword.
                - %s.hr_employee he: employees; company/branch, name, job, status, contact fields.
                - %s.hr_shift hs: HR work shifts and schedules.
                - %s.hr_employee_shift hes: employee shift assignments.
                - %s.hr_attendance_log hal: attendance clock/log events.
                - %s.hr_attendance_day had: daily attendance summaries.
                - %s.payroll_settings ps: payroll configuration.
                - %s.payroll_allowance_type pat: allowance type setup.
                - %s.payroll_deduction_type pdt: deduction type setup.
                - %s.payroll_salary_profile psp: employee salary profiles.
                - %s.payroll_salary_component psc: salary profile components.
                - %s.payroll_adjustment pa: payroll adjustments.
                - %s.payroll_run pr: payroll runs by period/status.
                - %s.payroll_run_line prl: payroll run employee results.
                - %s.payroll_run_line_component prlc: payroll calculation components.
                - %s.payroll_payment ppym: payroll payment batches.
                - %s.payroll_payment_line ppl: payroll employee payment lines.
                - %s.payroll_audit_log pal: payroll audit activity.

                Tenant offline POS sync:
                - %s.pos_device pd: registered POS devices. Do not select secret/token columns if present.
                - %s.pos_device_session pds: POS device sessions. Do not select secret/token columns if present.
                - %s.pos_sync_batch psb: offline sync batches.
                - %s.pos_offline_order_import pooi: imported offline POS orders.
                - %s.pos_offline_order_error pooe: offline order import errors.
                - %s.pos_idempotency_key pik: offline sync idempotency tracking. Do not select key hash/token values.
                - %s.pos_bootstrap_version pbv: POS bootstrap data versions.
                - %s.pos_sync_audit_log psal: offline sync audit logs.

                %s

                Public company/finance tables. Always filter public company-scoped tables with company_id = :companyId:
                - public."Company" pub_company: company master data.
                - public."Branch" pub_branch: branch master data. Filter "companyId" = :companyId and "branchId" = :branchId when branch-specific.
                - public."CompanySubscription" pub_sub: subscriptions.
                - public.finance_fiscal_year, public.finance_fiscal_period: finance periods.
                - public.finance_cost_center: finance cost centers.
                - public.finance_account: chart of accounts.
                - public.finance_account_mapping: operational-to-account mapping.
                - public.finance_journal_entry: accounting journal headers.
                - public.finance_journal_line: accounting journal lines.
                - public.finance_journal_sequence: journal numbering.
                - public.finance_tax_code, public.finance_tax_line: tax configuration and tax lines.
                - public.finance_account_balance: account balances.
                - public.finance_posting_batch, public.finance_posting_request: finance posting workflow.
                - public.finance_reconciliation_run, public.finance_reconciliation_item, public.finance_reconciliation_source_item: reconciliation workflow.
                - public.finance_period_close_run: period close workflow.
                - public.finance_audit_event: finance audit events.
                - public.finance_trial_balance_snapshot: trial balance snapshots.
                - public.branch_setting_groups, public.branch_setting_definitions, public.branch_setting_values: branch settings.
                - public.business_package_groups, public.business_package_categories, public.business_package_subcategories, public.business_packages: package catalog.
                - public.package_plans, public.package_module_policies: plan/package rules.
                - public.company_templates, public.company_template_module_defaults, public.company_template_workflow_defaults: company template defaults.
                - public.tenant_module_overrides, public.tenant_workflow_overrides, public.tenant_role_assignments, public.tenant_user_grant_overrides: tenant overrides/grants.
                - public.platform_modules, public.platform_capabilities, public.role_definitions, public.role_grants: platform capability metadata.
                - public.branch_runtime_states, public.tenant_lifecycle_events, public.branch_lifecycle_events: runtime/lifecycle state.
                - public.tenant_daily_metrics, public.branch_daily_metrics: platform metrics.
                - public.tenants, public.public_tenants: tenant registry/public tenant info.
                - public.billing_accounts, public.billing_prices, public.billing_invoices, public.billing_invoice_lines, public.billing_payment_attempts, public.billing_payment_methods, public.billing_provider_events, public.billing_dunning_runs, public.billing_entitlement_events, public.branch_subscriptions: billing/subscription data.
                - public.platform_admin_audit_log, public.platform_support_notes, public.platform_admin_alert_acknowledgments, public.platform_alert_notification_outbox: platform admin/support operations.
                - public.ai_conversation, public.ai_message, public.ai_tool_audit, public.ai_usage_log, public.ai_document, public.ai_document_chunk: AI conversation, audit, usage, and RAG document logs for this company. Filter company_id = :companyId when present.
                - public.pos_device, public.pos_device_session, public.pos_sync_batch, public.pos_offline_order_import, public.pos_offline_order_error, public.pos_idempotency_key, public.pos_bootstrap_version, public.pos_sync_audit_log: deprecated public offline-sync compatibility tables. Prefer tenant offline POS sync tables.

                Business rules:
                - Use only approved tables above.
                - Use :companyId for every public table with company_id, "companyId", or tenant/company ownership.
                - Use :branchId for every table with branch_id, "branchId", or "BranchId".
                - Dynamic branch tables already belong to the selected branch and do not need a branch column filter.
                - Return one PostgreSQL SELECT statement only.
                - Never use SELECT * or alias.*. Select explicit columns only.
                - Never select password, token, secret, credential, API key, key hash, or raw document content fields.
                - For counts, prefer COUNT(DISTINCT p.product_id) when counting products.
                - For non-aggregate lists, include LIMIT 50 or less.
                """.formatted(
                repeatSchema(schema, 77, dynamicBranchTables)
        );
    }

    private Object[] repeatSchema(String schema, int count, String dynamicBranchTables) {
        Object[] values = new Object[count + 1];
        for (int i = 0; i < count; i++) {
            values[i] = schema;
        }
        values[count] = dynamicBranchTables;
        return values;
    }

    public Set<String> allowedTables(long companyId, Long branchId) {
        String schema = TenantSqlIdentifiers.companySchema(companyId);
        Set<String> tables = new LinkedHashSet<>();

        addTenantTables(tables, schema);
        addPublicTables(tables);

        if (branchId != null) {
            int company = Math.toIntExact(companyId);
            int branch = Math.toIntExact(branchId);
            tables.add(normalizeTableName(TenantSqlIdentifiers.productTable(company, branch)));
            tables.add(normalizeTableName(TenantSqlIdentifiers.orderTable(company, branch)));
            tables.add(normalizeTableName(TenantSqlIdentifiers.orderDetailTable(company, branch)));
            tables.add(normalizeTableName(TenantSqlIdentifiers.supplierTable(company, branch)));
            tables.add(normalizeTableName(TenantSqlIdentifiers.inventoryTransactionsTable(company, branch)));
        }
        return tables;
    }

    private void addTenantTables(Set<String> tables, String schema) {
        String[] names = {
                "inventory_product", "inventory_branch_stock_balance", "inventory_stock_ledger",
                "inventory_legacy_product_mapping", "inventory_product_template", "inventory_template_attribute",
                "inventory_attribute_definition", "inventory_product_attribute_value", "inventory_uom_dimension",
                "inventory_uom_unit", "inventory_uom_conversion", "inventory_pricing_policy",
                "inventory_import_batch", "inventory_import_row", "inventory_import_error",
                "inventory_import_audit_log", "inventory_presets", "inventory_business_line",
                "\"Branch\"", "\"PosProduct\"", "\"InventoryTransactions\"", "\"PosOrder\"", "\"PosOrderDetail\"",
                "\"Client\"", "\"ClientReceipts\"", "\"supplierReciepts\"", "\"SupplierBProduct\"",
                "\"supplier \"", "\"DamagedList\"", "\"FixArea\"", "fix_area_parts", "\"Expenses\"",
                "\"ExpensesStatic\"", "\"ExpensesStaticHistory\"", "\"CompanyAnalysis\"", "\"MainMajor\"",
                "\"PosCateJson\"", "\"PosShiftPeriod\"", "shift_event", "shift_cash_movement", "pos_offers",
                "\"users\"", "hr_employee", "hr_shift", "hr_employee_shift", "hr_attendance_log",
                "hr_attendance_day", "payroll_settings", "payroll_allowance_type", "payroll_deduction_type",
                "payroll_salary_profile", "payroll_salary_component", "payroll_adjustment", "payroll_run",
                "payroll_run_line", "payroll_run_line_component", "payroll_payment", "payroll_payment_line",
                "payroll_audit_log", "pos_device", "pos_device_session", "pos_sync_batch",
                "pos_offline_order_import", "pos_offline_order_error", "pos_idempotency_key",
                "pos_bootstrap_version", "pos_sync_audit_log"
        };
        for (String name : names) {
            tables.add(normalizeTableName(schema + "." + name));
        }
    }

    private void addPublicTables(Set<String> tables) {
        String[] names = {
                "\"Company\"", "\"Branch\"", "\"CompanySubscription\"", "finance_fiscal_year",
                "finance_fiscal_period", "finance_cost_center", "finance_account_mapping",
                "finance_journal_sequence", "finance_posting_batch", "finance_posting_request",
                "finance_account", "finance_journal_line", "finance_journal_entry", "finance_tax_code",
                "finance_tax_line", "finance_account_balance", "finance_reconciliation_run",
                "finance_period_close_run", "finance_audit_event", "finance_trial_balance_snapshot",
                "finance_reconciliation_source_item", "finance_reconciliation_item", "branch_setting_groups",
                "branch_setting_definitions", "branch_setting_values", "platform_modules",
                "role_definitions", "role_grants", "platform_capabilities", "package_plans",
                "package_module_policies", "company_templates", "company_template_module_defaults",
                "tenant_module_overrides", "company_template_workflow_defaults", "tenant_workflow_overrides",
                "tenant_role_assignments", "tenant_user_grant_overrides", "onboarding_states",
                "branch_runtime_states", "tenant_lifecycle_events", "branch_lifecycle_events",
                "platform_admin_audit_log", "platform_support_notes", "tenant_daily_metrics",
                "branch_daily_metrics", "platform_admin_alert_acknowledgments", "tenants",
                "platform_alert_notification_outbox", "public_tenants", "billing_prices",
                "billing_invoice_lines", "billing_payment_attempts", "billing_provider_events",
                "billing_payment_methods", "billing_accounts", "billing_dunning_runs",
                "branch_subscriptions", "billing_invoices", "billing_entitlement_events",
                "business_package_groups", "business_package_categories", "business_package_subcategories",
                "business_packages", "ai_conversation", "ai_message", "ai_tool_audit",
                "ai_usage_log", "ai_document", "ai_document_chunk", "pos_device",
                "pos_device_session", "pos_sync_batch", "pos_offline_order_import",
                "pos_offline_order_error", "pos_idempotency_key", "pos_bootstrap_version",
                "pos_sync_audit_log"
        };
        for (String name : names) {
            tables.add(normalizeTableName("public." + name));
        }
    }

    public String normalizeTableName(String value) {
        return value == null
                ? ""
                : value.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }
}
