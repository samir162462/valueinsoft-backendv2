-- =====================================================================
-- V142: Accounts Payable subledger foundation (per tenant schema c_<companyId>).
--
-- Spec: docs/ar-ap-credit-open-items/OPEN_ITEMS_REVISED_SCHEMA_PLAN.md (§2.1, §2.2, §5, §7)
--
-- Mirror of V141 for suppliers, with the AP-specific rules:
--   * supplier_id has NO foreign key: supplier masters live in per-branch
--     tables (supplier_<branchId>), so referential integrity is a service-
--     layer responsibility (same as the existing "supplierReciepts" table).
--   * The party key is ALWAYS (branch_id, supplier_id) — supplier ids repeat
--     across branches. Uniqueness and coherence checks include branch_id.
--   * payment_terms_days is added to every existing supplier_<branchId>
--     table so due dates can be resolved at document creation. New branch
--     tables get the column from DbBranch DDL (Stage 2.2 — application
--     change in the same release; this loop only patches EXISTING tables).
--
-- No backfill: ap_open_item starts empty. Historical balances enter through
-- the gated import job only (OPEN_ITEMS_BACKFILL_DECISION.md — the ledger's
-- remaining_amount is client-maintained and unverifiable, review F3).
--
-- Idempotent; reused by DbCompany tenant bootstrap (Stage 2.1).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Guard trigger functions
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.ap_open_item_guard()
RETURNS TRIGGER AS $$
DECLARE
    old_j JSONB;
    new_j JSONB;
    mutable_keys TEXT[] := ARRAY[
        'settled_amount', 'remaining_amount', 'status', 'due_date', 'notes',
        'posting_request_id', 'journal_entry_id',
        'updated_by', 'updated_at', 'version'
    ];
    k TEXT;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        RAISE EXCEPTION 'ap_open_item % is append-only and cannot be deleted (use reversal)',
            OLD.open_item_id
            USING ERRCODE = 'check_violation';
    END IF;

    old_j := to_jsonb(OLD);
    new_j := to_jsonb(NEW);
    FOREACH k IN ARRAY mutable_keys LOOP
        old_j := old_j - k;
        new_j := new_j - k;
    END LOOP;
    IF old_j <> new_j THEN
        RAISE EXCEPTION 'ap_open_item %: only settlement/status/audit columns may change (identity and total_amount are immutable)',
            OLD.open_item_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.ap_payment_allocation_insert_guard()
RETURNS TRIGGER AS $$
DECLARE
    oi RECORD;
    rc RECORD;
    orig RECORD;
    active_sum NUMERIC(19,4);
BEGIN
    IF NEW.status <> 'POSTED' THEN
        RAISE EXCEPTION 'ap_payment_allocation must be inserted as POSTED'
            USING ERRCODE = 'check_violation';
    END IF;

    EXECUTE format(
        'SELECT company_id, branch_id, supplier_id, currency_code, remaining_amount, status
           FROM %I.ap_open_item WHERE open_item_id = $1',
        TG_TABLE_SCHEMA)
    INTO oi USING NEW.open_item_id;

    IF oi IS NULL THEN
        RAISE EXCEPTION 'ap_payment_allocation: open item % not found', NEW.open_item_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;
    -- AP party key is (branch_id, supplier_id): both must match (review F12).
    IF oi.company_id <> NEW.company_id
       OR oi.branch_id <> NEW.branch_id
       OR oi.supplier_id <> NEW.supplier_id THEN
        RAISE EXCEPTION 'ap_payment_allocation: company/branch/supplier mismatch with open item %', NEW.open_item_id
            USING ERRCODE = 'check_violation';
    END IF;
    IF oi.currency_code <> NEW.currency_code THEN
        RAISE EXCEPTION 'ap_payment_allocation: currency % does not match open item currency %',
            NEW.currency_code, oi.currency_code
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.reversal_of_allocation_id IS NULL THEN
        IF oi.status NOT IN ('OPEN', 'PARTIALLY_SETTLED') THEN
            RAISE EXCEPTION 'ap_payment_allocation: open item % is % and cannot receive allocations',
                NEW.open_item_id, oi.status
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.amount > oi.remaining_amount THEN
            RAISE EXCEPTION 'ap_payment_allocation: amount % exceeds open item remaining %',
                NEW.amount, oi.remaining_amount
                USING ERRCODE = 'check_violation';
        END IF;
    ELSE
        EXECUTE format(
            'SELECT receipt_id, open_item_id, amount, status, reversal_of_allocation_id
               FROM %I.ap_payment_allocation WHERE allocation_id = $1',
            TG_TABLE_SCHEMA)
        INTO orig USING NEW.reversal_of_allocation_id;
        IF orig IS NULL
           OR orig.status <> 'POSTED'
           OR orig.reversal_of_allocation_id IS NOT NULL
           OR orig.receipt_id IS DISTINCT FROM NEW.receipt_id
           OR orig.open_item_id <> NEW.open_item_id
           OR orig.amount <> NEW.amount THEN
            RAISE EXCEPTION 'ap_payment_allocation: reversal must mirror an active POSTED allocation exactly (target %)',
                NEW.reversal_of_allocation_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    IF NEW.receipt_id IS NOT NULL THEN
        EXECUTE format(
            'SELECT "supplierId" AS supplier_id, "branchId" AS branch_id,
                    "amountPaid"::numeric AS amount
               FROM %I."supplierReciepts" WHERE "srId" = $1',
            TG_TABLE_SCHEMA)
        INTO rc USING NEW.receipt_id;
        IF rc IS NULL THEN
            RAISE EXCEPTION 'ap_payment_allocation: supplier receipt % not found', NEW.receipt_id
                USING ERRCODE = 'foreign_key_violation';
        END IF;
        IF rc.supplier_id <> NEW.supplier_id OR rc.branch_id <> NEW.branch_id THEN
            RAISE EXCEPTION 'ap_payment_allocation: receipt % party (branch %, supplier %) does not match allocation',
                NEW.receipt_id, rc.branch_id, rc.supplier_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reversal_of_allocation_id IS NULL THEN
            EXECUTE format(
                'SELECT COALESCE(SUM(amount), 0)
                   FROM %I.ap_payment_allocation
                  WHERE receipt_id = $1 AND status = ''POSTED''
                    AND reversal_of_allocation_id IS NULL',
                TG_TABLE_SCHEMA)
            INTO active_sum USING NEW.receipt_id;
            IF active_sum + NEW.amount > rc.amount THEN
                RAISE EXCEPTION 'ap_payment_allocation: allocations (% + %) would exceed receipt % amount %',
                    active_sum, NEW.amount, NEW.receipt_id, rc.amount
                    USING ERRCODE = 'check_violation';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.ap_payment_allocation_mutation_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        RAISE EXCEPTION 'ap_payment_allocation % is append-only and cannot be deleted (use reversal)',
            OLD.allocation_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF OLD.status = 'POSTED' AND NEW.status = 'REVERSED'
       AND (to_jsonb(NEW) - 'status') = (to_jsonb(OLD) - 'status') THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'ap_payment_allocation % is immutable (only the POSTED -> REVERSED transition is permitted)',
        OLD.allocation_id
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- Per-tenant structures
-- ---------------------------------------------------------------------
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
    -- 1. ap_open_item
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ap_open_item (
            open_item_id       BIGSERIAL PRIMARY KEY,
            company_id         INTEGER NOT NULL,
            branch_id          INTEGER NOT NULL,
            supplier_id        INTEGER NOT NULL,
            source_type        VARCHAR(30) NOT NULL,
            source_id          BIGINT,
            document_ref       VARCHAR(64),
            document_date      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            due_date           TIMESTAMP,
            currency_code      VARCHAR(5) NOT NULL,
            total_amount       NUMERIC(19,4) NOT NULL,
            settled_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
            remaining_amount   NUMERIC(19,4) NOT NULL,
            status             VARCHAR(20) NOT NULL DEFAULT ''OPEN'',
            posting_request_id UUID,
            journal_entry_id   UUID,
            idempotency_key    VARCHAR(160),
            reversal_of_open_item_id BIGINT,
            notes              VARCHAR(255),
            created_by         VARCHAR(120),
            created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by         VARCHAR(120),
            updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version            BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT ap_oi_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT ap_oi_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT ap_oi_reversal_fk
                FOREIGN KEY (reversal_of_open_item_id) REFERENCES %I.ap_open_item (open_item_id),
            CONSTRAINT ap_oi_source_type_ck
                CHECK (source_type IN (''PURCHASE'', ''OPENING_BALANCE'', ''ADJUSTMENT'')),
            CONSTRAINT ap_oi_status_ck
                CHECK (status IN (''OPEN'', ''PARTIALLY_SETTLED'', ''SETTLED'', ''REVERSED'')),
            CONSTRAINT ap_oi_amounts_nonneg_ck
                CHECK (total_amount >= 0 AND settled_amount >= 0 AND remaining_amount >= 0),
            CONSTRAINT ap_oi_amounts_sum_ck
                CHECK (settled_amount + remaining_amount = total_amount),
            CONSTRAINT ap_oi_status_balance_ck CHECK (
                (status = ''OPEN''              AND settled_amount = 0 AND remaining_amount = total_amount) OR
                (status = ''PARTIALLY_SETTLED'' AND settled_amount > 0 AND remaining_amount > 0) OR
                (status = ''SETTLED''           AND remaining_amount = 0) OR
                (status = ''REVERSED''          AND remaining_amount = 0)
            )
        )
    ', target_schema, target_schema);

    -- branch_id is part of the AP uniqueness key on purpose (per-branch suppliers).
    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (company_id, branch_id, source_type, source_id)
            WHERE source_id IS NOT NULL
    ', 'ux_' || target_schema || '_ap_oi_source', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (company_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL
    ', 'ux_' || target_schema || '_ap_oi_idempotency', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (branch_id, supplier_id, status, due_date)
    ', 'idx_' || target_schema || '_ap_oi_supplier_status_due', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ap_open_item (branch_id, document_date DESC)
    ', 'idx_' || target_schema || '_ap_oi_branch_date', target_schema);

    EXECUTE format('DROP TRIGGER IF EXISTS trg_ap_open_item_guard ON %I.ap_open_item', target_schema);
    EXECUTE format('
        CREATE TRIGGER trg_ap_open_item_guard
            BEFORE UPDATE OR DELETE ON %I.ap_open_item
            FOR EACH ROW EXECUTE FUNCTION public.ap_open_item_guard()
    ', target_schema);

    -- ------------------------------------------------------------------
    -- 2. ap_payment_allocation
    -- ------------------------------------------------------------------
    IF receipts_table IS NULL THEN
        RAISE NOTICE 'Skipping ap_payment_allocation for %, "supplierReciepts" table does not exist', target_schema;
    ELSE
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.ap_payment_allocation (
                allocation_id      BIGSERIAL PRIMARY KEY,
                company_id         INTEGER NOT NULL,
                branch_id          INTEGER NOT NULL,
                supplier_id        INTEGER NOT NULL,
                receipt_id         INTEGER NOT NULL,
                open_item_id       BIGINT NOT NULL,
                amount             NUMERIC(19,4) NOT NULL,
                currency_code      VARCHAR(5) NOT NULL,
                status             VARCHAR(20) NOT NULL DEFAULT ''POSTED'',
                reversal_of_allocation_id BIGINT,
                idempotency_key    VARCHAR(160) NOT NULL,
                posting_request_id UUID,
                created_by         VARCHAR(120),
                created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT ap_alloc_company_fk
                    FOREIGN KEY (company_id) REFERENCES public."Company" (id),
                CONSTRAINT ap_alloc_receipt_fk
                    FOREIGN KEY (receipt_id) REFERENCES %I."supplierReciepts" ("srId") ON DELETE RESTRICT,
                CONSTRAINT ap_alloc_open_item_fk
                    FOREIGN KEY (open_item_id) REFERENCES %I.ap_open_item (open_item_id) ON DELETE RESTRICT,
                CONSTRAINT ap_alloc_reversal_fk
                    FOREIGN KEY (reversal_of_allocation_id) REFERENCES %I.ap_payment_allocation (allocation_id),
                CONSTRAINT ap_alloc_amount_ck CHECK (amount > 0),
                CONSTRAINT ap_alloc_status_ck CHECK (status IN (''POSTED'', ''REVERSED'')),
                CONSTRAINT ap_alloc_idem_uq UNIQUE (company_id, idempotency_key)
            )
        ', target_schema, target_schema, target_schema, target_schema);

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I.ap_payment_allocation (receipt_id, open_item_id)
                WHERE status = ''POSTED'' AND reversal_of_allocation_id IS NULL
        ', 'ux_' || target_schema || '_ap_alloc_active', target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.ap_payment_allocation (open_item_id)
        ', 'idx_' || target_schema || '_ap_alloc_open_item', target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.ap_payment_allocation (receipt_id)
        ', 'idx_' || target_schema || '_ap_alloc_receipt', target_schema);

        EXECUTE format('DROP TRIGGER IF EXISTS trg_ap_alloc_insert_guard ON %I.ap_payment_allocation', target_schema);
        EXECUTE format('
            CREATE TRIGGER trg_ap_alloc_insert_guard
                BEFORE INSERT ON %I.ap_payment_allocation
                FOR EACH ROW EXECUTE FUNCTION public.ap_payment_allocation_insert_guard()
        ', target_schema);

        EXECUTE format('DROP TRIGGER IF EXISTS trg_ap_alloc_mutation_guard ON %I.ap_payment_allocation', target_schema);
        EXECUTE format('
            CREATE TRIGGER trg_ap_alloc_mutation_guard
                BEFORE UPDATE OR DELETE ON %I.ap_payment_allocation
                FOR EACH ROW EXECUTE FUNCTION public.ap_payment_allocation_mutation_guard()
        ', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- 3. payment_terms_days on existing per-branch supplier tables
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

-- ---------------------------------------------------------------------
-- Apply to all existing tenants
-- ---------------------------------------------------------------------
DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN
        SELECT id FROM public."Company" ORDER BY id
    LOOP
        schema_name := format('c_%s', company_record.id);
        IF to_regnamespace(schema_name) IS NOT NULL THEN
            PERFORM public.ensure_ap_open_items_foundation_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
