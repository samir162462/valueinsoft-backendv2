-- V142__ap_open_items_and_supplier_terms.sql
--
-- DRAFT (review before moving to src/main/resources/db/migration/)
--
-- Accounts Payable subledger foundation, per tenant schema (c_<companyId>):
--   1. ap_open_item: one row per payable document (purchase, opening balance,
--      adjustment, debit note). supplier_id has NO foreign key because
--      supplier master rows live in per-branch tables (supplier_<branchId>);
--      integrity is enforced in the service layer, consistent with the
--      existing "supplierReciepts" table.
--   2. ap_payment_allocation: links "supplierReciepts" (srId) to open items.
--      The legacy single-document link ("supplierReciepts"."transId") remains
--      for backward compatibility; new payments write allocation rows and may
--      settle multiple documents.
--   3. supplier_<branchId>: payment_terms_days column for due-date-based aging.
--
-- Same idempotent tenant-function + company-loop pattern as V133/V141.

CREATE OR REPLACE FUNCTION public.ensure_ap_open_items_foundation_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    receipts_table REGCLASS;
    supplier_branch_table REGCLASS;
    branch_record RECORD;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    receipts_table := to_regclass(format('%I.%I', target_schema, 'supplierReciepts'));

    -- ------------------------------------------------------------------
    -- 1. AP open items
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ap_open_item (
            open_item_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            supplier_id INTEGER NOT NULL,
            source_type VARCHAR(30) NOT NULL,
            source_id BIGINT,
            document_ref VARCHAR(64),
            document_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            due_date TIMESTAMP,
            total_amount NUMERIC(19,4) NOT NULL,
            settled_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
            remaining_amount NUMERIC(19,4) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''OPEN'',
            posting_request_id UUID,
            idempotency_key VARCHAR(160),
            notes VARCHAR(255),
            created_by VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by VARCHAR(120),
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT ap_open_item_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT ap_open_item_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT ap_open_item_source_type_ck
                CHECK (source_type IN (''PURCHASE'', ''OPENING_BALANCE'', ''ADJUSTMENT'', ''DEBIT_NOTE'')),
            CONSTRAINT ap_open_item_status_ck
                CHECK (status IN (''OPEN'', ''PARTIALLY_SETTLED'', ''SETTLED'', ''REVERSED'')),
            CONSTRAINT ap_open_item_amounts_ck
                CHECK (settled_amount + remaining_amount = total_amount)
        )
    ', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (company_id, branch_id, source_type, source_id)
            WHERE source_id IS NOT NULL
    ', 'ux_' || target_schema || '_ap_open_item_source', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (company_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL
    ', 'ux_' || target_schema || '_ap_open_item_idempotency', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (company_id, supplier_id, status, due_date)
    ', 'idx_' || target_schema || '_ap_open_item_supplier_status', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (company_id, branch_id, document_date DESC)
    ', 'idx_' || target_schema || '_ap_open_item_branch_date', target_schema);

    -- ------------------------------------------------------------------
    -- 2. Payment allocations
    -- ------------------------------------------------------------------
    IF receipts_table IS NULL THEN
        RAISE NOTICE 'Skipping ap_payment_allocation for %, "supplierReciepts" table does not exist', target_schema;
    ELSE
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.ap_payment_allocation (
                allocation_id BIGSERIAL PRIMARY KEY,
                company_id INTEGER NOT NULL,
                receipt_id INTEGER NOT NULL,
                open_item_id BIGINT NOT NULL,
                amount NUMERIC(19,4) NOT NULL,
                created_by VARCHAR(120),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT ap_payment_allocation_receipt_fk
                    FOREIGN KEY (receipt_id) REFERENCES %I."supplierReciepts" ("srId") ON DELETE CASCADE,
                CONSTRAINT ap_payment_allocation_open_item_fk
                    FOREIGN KEY (open_item_id) REFERENCES %I.ap_open_item (open_item_id),
                CONSTRAINT ap_payment_allocation_amount_ck CHECK (amount > 0),
                CONSTRAINT ap_payment_allocation_unique UNIQUE (receipt_id, open_item_id)
            )
        ', target_schema, target_schema, target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.ap_payment_allocation (company_id, open_item_id)
        ', 'idx_' || target_schema || '_ap_payment_allocation_open_item', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- 3. Supplier payment terms (per-branch supplier tables)
    -- ------------------------------------------------------------------
    FOR branch_record IN
        SELECT "branchId"
        FROM public."Branch"
        WHERE "companyId" = target_company_id
        ORDER BY "branchId"
    LOOP
        supplier_branch_table := to_regclass(
            format('%I.%I', target_schema, 'supplier_' || branch_record."branchId"));
        IF supplier_branch_table IS NOT NULL THEN
            EXECUTE format('
                ALTER TABLE %I.%I
                    ADD COLUMN IF NOT EXISTS payment_terms_days INTEGER NOT NULL DEFAULT 0
            ', target_schema, 'supplier_' || branch_record."branchId");
        END IF;
    END LOOP;
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
            PERFORM public.ensure_ap_open_items_foundation_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
