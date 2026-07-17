-- V143__ar_ap_open_items_backfill_and_capabilities.sql
--
-- DRAFT (review before moving to src/main/resources/db/migration/)
--
-- 1. AP backfill: one PURCHASE open item per inventory_stock_ledger row with
--    remaining_amount > 0 and a supplier. settled/remaining come from the
--    ledger row itself ("supplierReciepts" already decremented remaining in
--    place, so remaining_amount is authoritative today).
-- 2. AR backfill: per-order reconstruction is NOT possible (order pay types
--    are free text and orders carry no remaining amount), so each client gets
--    a single OPENING_BALANCE open item = credit-classified order totals
--    minus all client receipts, when positive. The GL control account (1100)
--    remains the source of truth for company-level totals.
-- 3. Capability and role-grant seeds following the V66 pattern.
--
-- Idempotent: open items carry deterministic idempotency keys; reruns skip
-- existing rows.

-- =====================================================================
-- AP backfill
-- =====================================================================
CREATE OR REPLACE FUNCTION public.backfill_ap_open_items_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    ledger_table REGCLASS;
    open_item_table REGCLASS;
BEGIN
    ledger_table    := to_regclass(format('%I.%I', target_schema, 'inventory_stock_ledger'));
    open_item_table := to_regclass(format('%I.%I', target_schema, 'ap_open_item'));

    IF ledger_table IS NULL OR open_item_table IS NULL THEN
        RAISE NOTICE 'Skipping AP backfill for %', target_schema;
        RETURN;
    END IF;

    EXECUTE format('
        INSERT INTO %I.ap_open_item (
            company_id, branch_id, supplier_id, source_type, source_id,
            document_ref, document_date, due_date,
            total_amount, settled_amount, remaining_amount,
            status, idempotency_key, created_by
        )
        SELECT
            %s,
            ledger.branch_id,
            ledger.supplier_id,
            ''PURCHASE'',
            ledger.stock_ledger_id,
            ''ledger-'' || ledger.stock_ledger_id::text,
            ledger.created_at,
            ledger.created_at,
            COALESCE(ledger.trans_total, 0)::numeric,
            (COALESCE(ledger.trans_total, 0) - COALESCE(ledger.remaining_amount, 0))::numeric,
            COALESCE(ledger.remaining_amount, 0)::numeric,
            CASE
                WHEN COALESCE(ledger.remaining_amount, 0) = COALESCE(ledger.trans_total, 0) THEN ''OPEN''
                ELSE ''PARTIALLY_SETTLED''
            END,
            ''backfill-ap-'' || ledger.stock_ledger_id::text,
            ''migration-v143''
        FROM %I.inventory_stock_ledger ledger
        WHERE COALESCE(ledger.supplier_id, 0) > 0
          AND COALESCE(ledger.remaining_amount, 0) > 0
          AND COALESCE(ledger.trans_total, 0) >= COALESCE(ledger.remaining_amount, 0)
        ON CONFLICT DO NOTHING
    ', target_schema, target_company_id, target_schema);
END;
$$;

-- =====================================================================
-- AR backfill (client-level opening balances)
-- =====================================================================
CREATE OR REPLACE FUNCTION public.backfill_ar_opening_balances_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    client_table REGCLASS;
    receipts_table REGCLASS;
    open_item_table REGCLASS;
    branch_record RECORD;
    order_table_name TEXT;
    union_sql TEXT := '';
BEGIN
    client_table    := to_regclass(format('%I.%I', target_schema, 'Client'));
    receipts_table  := to_regclass(format('%I.%I', target_schema, 'ClientReceipts'));
    open_item_table := to_regclass(format('%I.%I', target_schema, 'ar_open_item'));

    IF client_table IS NULL OR receipts_table IS NULL OR open_item_table IS NULL THEN
        RAISE NOTICE 'Skipping AR backfill for %', target_schema;
        RETURN;
    END IF;

    -- Union credit-classified orders across per-branch order tables.
    -- Classification mirrors FinanceDailyCashClosingReportService.
    FOR branch_record IN
        SELECT "branchId"
        FROM public."Branch"
        WHERE "companyId" = target_company_id
        ORDER BY "branchId"
    LOOP
        order_table_name := 'PosOrder_' || branch_record."branchId";
        IF to_regclass(format('%I.%I', target_schema, order_table_name)) IS NOT NULL THEN
            IF union_sql <> '' THEN
                union_sql := union_sql || ' UNION ALL ';
            END IF;
            union_sql := union_sql || format('
                SELECT "clientId" AS client_id, %s AS branch_id,
                       COALESCE("orderTotal", 0)::numeric AS order_total
                FROM %I.%I
                WHERE "clientId" IS NOT NULL AND "clientId" > 0
                  AND (
                        lower(COALESCE("orderType", '''')) LIKE ''%%credit%%''
                     OR lower(COALESCE("orderType", '''')) LIKE ''%%later%%''
                     OR lower(COALESCE("orderType", '''')) LIKE ''%%debt%%''
                     OR lower(COALESCE("orderType", '''')) LIKE ''%%receivable%%''
                  )',
                branch_record."branchId", target_schema, order_table_name);
        END IF;
    END LOOP;

    IF union_sql = '' THEN
        RAISE NOTICE 'No order tables found for %, skipping AR backfill', target_schema;
        RETURN;
    END IF;

    EXECUTE format('
        INSERT INTO %I.ar_open_item (
            company_id, branch_id, client_id, source_type, source_id,
            document_ref, document_date, due_date,
            total_amount, settled_amount, remaining_amount,
            status, idempotency_key, created_by, notes
        )
        SELECT
            %s,
            balances.main_branch_id,
            balances.client_id,
            ''OPENING_BALANCE'',
            NULL,
            ''opening-client-'' || balances.client_id::text,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            balances.open_balance,
            0,
            balances.open_balance,
            ''OPEN'',
            ''backfill-ar-opening-'' || balances.client_id::text,
            ''migration-v143'',
            ''Backfilled: historical credit orders minus receipts''
        FROM (
            SELECT
                credit_orders.client_id,
                MIN(credit_orders.branch_id) AS main_branch_id,
                SUM(credit_orders.order_total)
                    - COALESCE((
                        SELECT SUM(r.amount::numeric)
                        FROM %I."ClientReceipts" r
                        WHERE r."clientId" = credit_orders.client_id
                      ), 0) AS open_balance
            FROM ( %s ) credit_orders
            GROUP BY credit_orders.client_id
        ) balances
        JOIN %I."Client" c ON c.c_id = balances.client_id
        WHERE balances.open_balance > 0
        ON CONFLICT DO NOTHING
    ', target_schema, target_company_id, target_schema, union_sql, target_schema);
END;
$$;

DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN
        SELECT id
        FROM public."Company"
        ORDER BY id
    LOOP
        schema_name := format('c_%s', company_record.id);
        IF to_regnamespace(schema_name) IS NOT NULL THEN
            PERFORM public.backfill_ap_open_items_for_tenant(schema_name, company_record.id);
            PERFORM public.backfill_ar_opening_balances_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;

-- =====================================================================
-- Capabilities and role grants (V66 pattern)
-- =====================================================================
INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES
    ('clients.credit.view',        'clients',   'credit',    'view',     'company', 'active', 'View client credit limit, terms and exposure.'),
    ('clients.credit.manage',      'clients',   'credit',    'manage',   'company', 'active', 'Set client credit limit, terms and credit status.'),
    -- NOTE: clients.statement.view already exists (V134, trade-in seller statement).
    -- Re-seeded here intentionally with a broader description covering the full
    -- client account statement; existing role grants are unaffected.
    ('clients.statement.view',     'clients',   'statement', 'view',     'company', 'active', 'View client account statements and aging.'),
    ('clients.openitems.view',     'clients',   'openitems', 'view',     'company', 'active', 'View client open items (unpaid documents).'),
    ('clients.openitems.allocate', 'clients',   'openitems', 'allocate', 'company', 'active', 'Allocate client receipts to open items.'),
    ('clients.creditnote.create',  'clients',   'creditnote','create',   'company', 'active', 'Create a client credit note.'),
    ('clients.creditnote.reverse', 'clients',   'creditnote','reverse',  'company', 'active', 'Reverse a client credit note.'),
    ('suppliers.openitems.view',     'suppliers', 'openitems', 'view',     'company', 'active', 'View supplier open items (unpaid documents).'),
    ('suppliers.openitems.allocate', 'suppliers', 'openitems', 'allocate', 'company', 'active', 'Allocate supplier payments to open items.'),
    ('suppliers.debitnote.create',   'suppliers', 'debitnote', 'create',   'company', 'active', 'Create a supplier debit note.'),
    ('suppliers.debitnote.reverse',  'suppliers', 'debitnote', 'reverse',  'company', 'active', 'Reverse a supplier debit note.')
ON CONFLICT (capability_key) DO UPDATE
SET module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

-- Owner: everything
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Owner', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.credit.view', 'clients.credit.manage', 'clients.statement.view',
    'clients.openitems.view', 'clients.openitems.allocate',
    'clients.creditnote.create', 'clients.creditnote.reverse',
    'suppliers.openitems.view', 'suppliers.openitems.allocate',
    'suppliers.debitnote.create', 'suppliers.debitnote.reverse'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode, grant_version = EXCLUDED.grant_version;

-- Accountant: financial subset
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Accountant', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.credit.view', 'clients.statement.view',
    'clients.openitems.view', 'clients.openitems.allocate',
    'clients.creditnote.create', 'clients.creditnote.reverse',
    'suppliers.openitems.view', 'suppliers.openitems.allocate',
    'suppliers.debitnote.create', 'suppliers.debitnote.reverse'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode, grant_version = EXCLUDED.grant_version;

-- BranchManager: view subset
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'BranchManager', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.credit.view', 'clients.statement.view', 'clients.openitems.view',
    'suppliers.openitems.view'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode, grant_version = EXCLUDED.grant_version;
