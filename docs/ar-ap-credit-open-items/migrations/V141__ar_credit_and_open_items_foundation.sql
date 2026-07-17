-- V141__ar_credit_and_open_items_foundation.sql
--
-- DRAFT (review before moving to src/main/resources/db/migration/)
--
-- Accounts Receivable subledger foundation, per tenant schema (c_<companyId>):
--   1. "Client": credit control columns (credit_limit, credit_terms_days,
--      credit_status, credit_notes).
--   2. ar_open_item: one row per receivable document (POS credit order,
--      opening balance, adjustment, credit note). Append-oriented; remaining
--      amount changes only via allocations or reversal documents.
--   3. ar_receipt_allocation: links "ClientReceipts" rows to open items,
--      enabling partial and multi-document settlement.
--
-- Mirrors the V133 client trade-in pattern (idempotent tenant function +
-- company loop, reusable by DbCompany tenant bootstrap). GL posting is
-- unchanged: control account 1100 remains fed by existing adapters; this
-- subledger must reconcile to it.

CREATE OR REPLACE FUNCTION public.ensure_ar_open_items_foundation_for_tenant(
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
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    client_table   := to_regclass(format('%I.%I', target_schema, 'Client'));
    receipts_table := to_regclass(format('%I.%I', target_schema, 'ClientReceipts'));

    IF client_table IS NULL THEN
        RAISE NOTICE 'Skipping AR foundation for %, "Client" table does not exist', target_schema;
        RETURN;
    END IF;

    -- ------------------------------------------------------------------
    -- 1. Client credit control columns
    -- ------------------------------------------------------------------
    EXECUTE format('
        ALTER TABLE %I."Client"
            ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0,
            ADD COLUMN IF NOT EXISTS credit_terms_days INTEGER NOT NULL DEFAULT 0,
            ADD COLUMN IF NOT EXISTS credit_status VARCHAR(20) NOT NULL DEFAULT ''NORMAL'',
            ADD COLUMN IF NOT EXISTS credit_notes VARCHAR(255)
    ', target_schema);

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = client_table AND conname = 'client_credit_status_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I."Client"
                ADD CONSTRAINT client_credit_status_ck
                CHECK (credit_status IN (''NORMAL'', ''HOLD'', ''BLOCKED''))
                NOT VALID
        ', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = client_table AND conname = 'client_credit_terms_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I."Client"
                ADD CONSTRAINT client_credit_terms_ck
                CHECK (credit_limit >= 0 AND credit_terms_days >= 0)
                NOT VALID
        ', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- 2. AR open items
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ar_open_item (
            open_item_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            client_id INTEGER NOT NULL,
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
            CONSTRAINT ar_open_item_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT ar_open_item_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT ar_open_item_client_fk
                FOREIGN KEY (client_id) REFERENCES %I."Client" (c_id),
            CONSTRAINT ar_open_item_source_type_ck
                CHECK (source_type IN (''POS_ORDER'', ''OPENING_BALANCE'', ''ADJUSTMENT'', ''CREDIT_NOTE'')),
            CONSTRAINT ar_open_item_status_ck
                CHECK (status IN (''OPEN'', ''PARTIALLY_SETTLED'', ''SETTLED'', ''REVERSED'')),
            CONSTRAINT ar_open_item_amounts_ck
                CHECK (settled_amount + remaining_amount = total_amount)
        )
    ', target_schema, target_schema);

    open_item_table := to_regclass(format('%I.%I', target_schema, 'ar_open_item'));

    -- One open item per source document (per branch: order ids repeat across
    -- the per-branch PosOrder_<branchId> tables).
    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (company_id, branch_id, source_type, source_id)
            WHERE source_id IS NOT NULL
    ', 'ux_' || target_schema || '_ar_open_item_source', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (company_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL
    ', 'ux_' || target_schema || '_ar_open_item_idempotency', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (company_id, client_id, status, due_date)
    ', 'idx_' || target_schema || '_ar_open_item_client_status', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (company_id, branch_id, document_date DESC)
    ', 'idx_' || target_schema || '_ar_open_item_branch_date', target_schema);

    -- ------------------------------------------------------------------
    -- 3. Receipt allocations
    -- ------------------------------------------------------------------
    IF receipts_table IS NULL THEN
        RAISE NOTICE 'Skipping ar_receipt_allocation for %, "ClientReceipts" table does not exist', target_schema;
        RETURN;
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ar_receipt_allocation (
            allocation_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            receipt_id INTEGER NOT NULL,
            open_item_id BIGINT NOT NULL,
            amount NUMERIC(19,4) NOT NULL,
            created_by VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT ar_receipt_allocation_receipt_fk
                FOREIGN KEY (receipt_id) REFERENCES %I."ClientReceipts" ("crId") ON DELETE CASCADE,
            CONSTRAINT ar_receipt_allocation_open_item_fk
                FOREIGN KEY (open_item_id) REFERENCES %I.ar_open_item (open_item_id),
            CONSTRAINT ar_receipt_allocation_amount_ck CHECK (amount > 0),
            CONSTRAINT ar_receipt_allocation_unique UNIQUE (receipt_id, open_item_id)
        )
    ', target_schema, target_schema, target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_receipt_allocation (company_id, open_item_id)
    ', 'idx_' || target_schema || '_ar_receipt_allocation_open_item', target_schema);
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
            PERFORM public.ensure_ar_open_items_foundation_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
