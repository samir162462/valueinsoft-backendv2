-- V133__client_tradein_party_and_condition_foundation.sql
--
-- Foundation for purchasing inventory from existing clients (customer trade-in).
--
-- Party-role model: the same tenant "Client" row can buy from the shop (existing
-- sales flows) and sell to the shop (this feature). No person duplication.
--
-- Adds, per tenant schema (c_<companyId>):
--   1. "Client": lifecycle status (ACTIVE/ARCHIVED), audit columns, version.
--   2. inventory_stock_ledger: acquisition party columns (source_party_type,
--      client_id) and receipt-line condition (condition_code, condition_notes)
--      for non-serialized stock.
--   3. inventory_product_unit: trade-in source columns (source_party_type,
--      source_client_id, condition_notes). Received-unit condition already
--      exists as condition_code (V97/V112).
--   4. client_tradein_receipt: authoritative payable subledger line per client
--      purchase receipt (DECIMAL(19,4) money, payment status).
--   5. client_tradein_payment (+ allocation table): idempotent payments made to
--      clients against their trade-in payables.
--   6. inventory_unit_condition_audit: auditable condition correction trail.
--
-- The helper function is reused by tenant bootstrap (DbCompany) so newly
-- provisioned companies receive the same structures. All steps are idempotent.

CREATE OR REPLACE FUNCTION public.ensure_client_tradein_foundation_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    client_table REGCLASS;
    ledger_table REGCLASS;
    unit_table REGCLASS;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    client_table := to_regclass(format('%I.%I', target_schema, 'Client'));
    ledger_table := to_regclass(format('%I.%I', target_schema, 'inventory_stock_ledger'));
    unit_table   := to_regclass(format('%I.%I', target_schema, 'inventory_product_unit'));

    -- ------------------------------------------------------------------
    -- 1. Client lifecycle + audit columns
    -- ------------------------------------------------------------------
    IF client_table IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I."Client"
                ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
                ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS created_by VARCHAR(120),
                ADD COLUMN IF NOT EXISTS updated_by VARCHAR(120),
                ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0
        ', target_schema);

        EXECUTE format('
            UPDATE %I."Client"
            SET status = ''ACTIVE''
            WHERE status IS NULL OR btrim(status) = ''''
        ', target_schema);

        EXECUTE format('
            UPDATE %I."Client"
            SET created_at = COALESCE(created_at, "registeredTime", CURRENT_TIMESTAMP),
                updated_at = COALESCE(updated_at, "registeredTime", CURRENT_TIMESTAMP)
        ', target_schema);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = client_table AND conname = 'client_status_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I."Client"
                    ADD CONSTRAINT client_status_ck
                    CHECK (status IN (''ACTIVE'', ''ARCHIVED''))
                    NOT VALID
            ', target_schema);
        END IF;

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I."Client" (status, c_id DESC)
        ', 'idx_' || target_schema || '_client_status', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- 2. Stock ledger: acquisition party + line condition
    -- ------------------------------------------------------------------
    IF ledger_table IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_stock_ledger
                ADD COLUMN IF NOT EXISTS source_party_type VARCHAR(20) NOT NULL DEFAULT ''SUPPLIER'',
                ADD COLUMN IF NOT EXISTS client_id INTEGER,
                ADD COLUMN IF NOT EXISTS condition_code VARCHAR(10) NOT NULL DEFAULT ''NEW'',
                ADD COLUMN IF NOT EXISTS condition_notes VARCHAR(255)
        ', target_schema);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ledger_table AND conname = 'inventory_stock_ledger_source_party_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_stock_ledger
                    ADD CONSTRAINT inventory_stock_ledger_source_party_ck
                    CHECK (source_party_type IN (''SUPPLIER'', ''CLIENT''))
                    NOT VALID
            ', target_schema);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ledger_table AND conname = 'inventory_stock_ledger_condition_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_stock_ledger
                    ADD CONSTRAINT inventory_stock_ledger_condition_ck
                    CHECK (condition_code IN (''NEW'', ''USED''))
                    NOT VALID
            ', target_schema);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ledger_table AND conname = 'inventory_stock_ledger_client_party_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_stock_ledger
                    ADD CONSTRAINT inventory_stock_ledger_client_party_ck
                    CHECK (source_party_type <> ''CLIENT'' OR client_id IS NOT NULL)
                    NOT VALID
            ', target_schema);
        END IF;

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.inventory_stock_ledger (company_id, client_id, created_at DESC)
                WHERE client_id IS NOT NULL
        ', 'idx_' || target_schema || '_inventory_stock_ledger_client', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- 3. Serialized units: trade-in source columns
    -- ------------------------------------------------------------------
    IF unit_table IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD COLUMN IF NOT EXISTS source_party_type VARCHAR(20) NOT NULL DEFAULT ''SUPPLIER'',
                ADD COLUMN IF NOT EXISTS source_client_id BIGINT,
                ADD COLUMN IF NOT EXISTS condition_notes VARCHAR(255)
        ', target_schema);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = unit_table AND conname = 'inventory_product_unit_source_party_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_product_unit
                    ADD CONSTRAINT inventory_product_unit_source_party_ck
                    CHECK (source_party_type IN (''SUPPLIER'', ''CLIENT''))
                    NOT VALID
            ', target_schema);
        END IF;

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.inventory_product_unit (company_id, source_client_id)
                WHERE source_client_id IS NOT NULL
        ', 'idx_' || target_schema || '_inventory_product_unit_source_client', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- 4. Condition correction audit trail (independent of "Client")
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.inventory_unit_condition_audit (
            audit_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            product_unit_id BIGINT,
            stock_ledger_id BIGINT,
            old_condition_code VARCHAR(10),
            new_condition_code VARCHAR(10) NOT NULL,
            reason VARCHAR(255) NOT NULL,
            actor_name VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT inventory_unit_condition_audit_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT inventory_unit_condition_audit_target_ck
                CHECK (product_unit_id IS NOT NULL OR stock_ledger_id IS NOT NULL),
            CONSTRAINT inventory_unit_condition_audit_new_condition_ck
                CHECK (new_condition_code IN (''NEW'', ''USED''))
        )
    ', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_unit_condition_audit (company_id, product_unit_id)
            WHERE product_unit_id IS NOT NULL
    ', 'idx_' || target_schema || '_inventory_unit_condition_audit_unit', target_schema);

    -- ------------------------------------------------------------------
    -- 5. Client trade-in receipt payable subledger
    -- (requires the tenant "Client" table for the party FK)
    -- ------------------------------------------------------------------
    IF client_table IS NULL THEN
        RAISE NOTICE 'Skipping client trade-in subledger for %, "Client" table does not exist', target_schema;
        RETURN;
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.client_tradein_receipt (
            tradein_receipt_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            client_id INTEGER NOT NULL,
            stock_ledger_id BIGINT NOT NULL,
            product_id BIGINT NOT NULL,
            receipt_reference VARCHAR(64) NOT NULL,
            quantity INTEGER NOT NULL,
            condition_code VARCHAR(10) NOT NULL DEFAULT ''NEW'',
            condition_notes VARCHAR(255),
            unit_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
            total_amount NUMERIC(19,4) NOT NULL,
            paid_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
            remaining_amount NUMERIC(19,4) NOT NULL,
            payment_status VARCHAR(20) NOT NULL,
            payment_method VARCHAR(30),
            status VARCHAR(20) NOT NULL DEFAULT ''POSTED'',
            idempotency_key VARCHAR(160),
            created_by VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by VARCHAR(120),
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT client_tradein_receipt_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT client_tradein_receipt_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT client_tradein_receipt_client_fk
                FOREIGN KEY (client_id) REFERENCES %I."Client" (c_id),
            CONSTRAINT client_tradein_receipt_quantity_ck CHECK (quantity > 0),
            CONSTRAINT client_tradein_receipt_condition_ck CHECK (condition_code IN (''NEW'', ''USED'')),
            CONSTRAINT client_tradein_receipt_amounts_ck
                CHECK (total_amount >= 0 AND paid_amount >= 0 AND remaining_amount >= 0
                       AND paid_amount + remaining_amount = total_amount),
            CONSTRAINT client_tradein_receipt_payment_status_ck
                CHECK (payment_status IN (''PAID'', ''PARTIALLY_PAID'', ''UNPAID'')),
            CONSTRAINT client_tradein_receipt_status_ck CHECK (status IN (''POSTED'', ''REVERSED''))
        )
    ', target_schema, target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.client_tradein_receipt (company_id, stock_ledger_id)
    ', 'ux_' || target_schema || '_client_tradein_receipt_ledger', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.client_tradein_receipt (company_id, client_id, created_at DESC)
    ', 'idx_' || target_schema || '_client_tradein_receipt_client', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.client_tradein_receipt (company_id, client_id, payment_status)
    ', 'idx_' || target_schema || '_client_tradein_receipt_payment_status', target_schema);

    -- ------------------------------------------------------------------
    -- 5. Payments to clients (idempotent) + allocations
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.client_tradein_payment (
            payment_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            client_id INTEGER NOT NULL,
            amount NUMERIC(19,4) NOT NULL,
            payment_method VARCHAR(30) NOT NULL,
            notes VARCHAR(255),
            idempotency_key VARCHAR(160) NOT NULL,
            request_hash VARCHAR(128),
            status VARCHAR(20) NOT NULL DEFAULT ''POSTED'',
            posting_request_id UUID,
            created_by VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by VARCHAR(120),
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT client_tradein_payment_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT client_tradein_payment_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT client_tradein_payment_client_fk
                FOREIGN KEY (client_id) REFERENCES %I."Client" (c_id),
            CONSTRAINT client_tradein_payment_amount_ck CHECK (amount > 0),
            CONSTRAINT client_tradein_payment_status_ck CHECK (status IN (''POSTED'', ''REVERSED''))
        )
    ', target_schema, target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.client_tradein_payment (company_id, idempotency_key)
    ', 'ux_' || target_schema || '_client_tradein_payment_idempotency', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.client_tradein_payment (company_id, client_id, created_at DESC)
    ', 'idx_' || target_schema || '_client_tradein_payment_client', target_schema);

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.client_tradein_payment_allocation (
            allocation_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            payment_id BIGINT NOT NULL,
            tradein_receipt_id BIGINT NOT NULL,
            amount NUMERIC(19,4) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT client_tradein_payment_allocation_payment_fk
                FOREIGN KEY (payment_id) REFERENCES %I.client_tradein_payment (payment_id) ON DELETE CASCADE,
            CONSTRAINT client_tradein_payment_allocation_receipt_fk
                FOREIGN KEY (tradein_receipt_id) REFERENCES %I.client_tradein_receipt (tradein_receipt_id),
            CONSTRAINT client_tradein_payment_allocation_amount_ck CHECK (amount > 0),
            CONSTRAINT client_tradein_payment_allocation_unique UNIQUE (payment_id, tradein_receipt_id)
        )
    ', target_schema, target_schema, target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.client_tradein_payment_allocation (company_id, tradein_receipt_id)
    ', 'idx_' || target_schema || '_client_tradein_payment_allocation_receipt', target_schema);
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
            PERFORM public.ensure_client_tradein_foundation_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
