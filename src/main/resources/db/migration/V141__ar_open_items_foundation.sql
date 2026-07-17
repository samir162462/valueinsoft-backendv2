-- =====================================================================
-- V141: Accounts Receivable subledger foundation (per tenant schema c_<companyId>).
--
-- Spec: docs/ar-ap-credit-open-items/OPEN_ITEMS_REVISED_SCHEMA_PLAN.md (§2.1, §2.2, §5)
-- Review basis: OPEN_ITEMS_MIGRATION_REVIEW.md blockers B1, B4, B5, B7, B10, B11.
--
-- Creates, per tenant:
--   1. "Client" credit-control columns (credit_limit, credit_terms_days,
--      credit_status, credit_notes).
--   2. ar_open_item — one row per receivable document. Append-oriented:
--      identity and total are immutable after insert; only settlement
--      summary columns may change; no DELETE ever.
--   3. ar_receipt_allocation — links "ClientReceipts" to open items.
--      Append-only; reversal rows instead of updates/deletes; RESTRICT FKs
--      (never CASCADE: deleting a receipt must not erase posted allocations).
--   4. Guard triggers (V139 style, defense-in-depth behind the service
--      FOR-UPDATE protocol): party/company/currency coherence, over-
--      allocation caps, append-only enforcement.
--
-- Deliberately ABSENT (see review):
--   * No CREDIT_NOTE source_type — credit notes are offset documents (V144).
--   * No historical backfill — opening balances enter only through the
--     gated import job (OPEN_ITEMS_BACKFILL_DECISION.md).
--
-- Idempotent; reused by DbCompany tenant bootstrap (Stage 2.1).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Guard trigger functions (generic across tenant schemas via TG_TABLE_SCHEMA)
-- ---------------------------------------------------------------------

-- ar_open_item: forbid DELETE; forbid UPDATE of identity/total columns.
-- WHY: subledger documents are corrected by reversal documents, never by
-- rewriting history (review F3/F6 showed where in-place mutation led).
CREATE OR REPLACE FUNCTION public.ar_open_item_guard()
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
        RAISE EXCEPTION 'ar_open_item % is append-only and cannot be deleted (use reversal)',
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
        RAISE EXCEPTION 'ar_open_item %: only settlement/status/audit columns may change (identity and total_amount are immutable)',
            OLD.open_item_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ar_receipt_allocation INSERT guard: coherence + caps.
-- WHY: any code path (offline sync, AI tools, manual SQL) that bypasses the
-- service protocol must still be unable to over-settle or cross parties (B5/B7).
CREATE OR REPLACE FUNCTION public.ar_receipt_allocation_insert_guard()
RETURNS TRIGGER AS $$
DECLARE
    oi RECORD;
    rc RECORD;
    orig RECORD;
    active_sum NUMERIC(19,4);
BEGIN
    IF NEW.status <> 'POSTED' THEN
        RAISE EXCEPTION 'ar_receipt_allocation must be inserted as POSTED (reversals are mirror rows, not pre-reversed inserts)'
            USING ERRCODE = 'check_violation';
    END IF;

    EXECUTE format(
        'SELECT company_id, client_id, currency_code, remaining_amount, status
           FROM %I.ar_open_item WHERE open_item_id = $1',
        TG_TABLE_SCHEMA)
    INTO oi USING NEW.open_item_id;

    IF oi IS NULL THEN
        RAISE EXCEPTION 'ar_receipt_allocation: open item % not found', NEW.open_item_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;
    IF oi.company_id <> NEW.company_id OR oi.client_id <> NEW.client_id THEN
        RAISE EXCEPTION 'ar_receipt_allocation: company/client mismatch with open item %', NEW.open_item_id
            USING ERRCODE = 'check_violation';
    END IF;
    IF oi.currency_code <> NEW.currency_code THEN
        RAISE EXCEPTION 'ar_receipt_allocation: currency % does not match open item currency %',
            NEW.currency_code, oi.currency_code
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.reversal_of_allocation_id IS NULL THEN
        -- Active allocation: open item must be settleable and must not over-settle.
        IF oi.status NOT IN ('OPEN', 'PARTIALLY_SETTLED') THEN
            RAISE EXCEPTION 'ar_receipt_allocation: open item % is % and cannot receive allocations',
                NEW.open_item_id, oi.status
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.amount > oi.remaining_amount THEN
            RAISE EXCEPTION 'ar_receipt_allocation: amount % exceeds open item remaining %',
                NEW.amount, oi.remaining_amount
                USING ERRCODE = 'check_violation';
        END IF;
    ELSE
        -- Reversal mirror row: must target a POSTED active allocation of the
        -- same (receipt, open item) pair with the exact same amount.
        EXECUTE format(
            'SELECT receipt_id, open_item_id, amount, status, reversal_of_allocation_id
               FROM %I.ar_receipt_allocation WHERE allocation_id = $1',
            TG_TABLE_SCHEMA)
        INTO orig USING NEW.reversal_of_allocation_id;
        IF orig IS NULL
           OR orig.status <> 'POSTED'
           OR orig.reversal_of_allocation_id IS NOT NULL
           OR orig.receipt_id IS DISTINCT FROM NEW.receipt_id
           OR orig.open_item_id <> NEW.open_item_id
           OR orig.amount <> NEW.amount THEN
            RAISE EXCEPTION 'ar_receipt_allocation: reversal must mirror an active POSTED allocation exactly (target %)',
                NEW.reversal_of_allocation_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    -- Receipt coherence + receipt-level cap.
    IF NEW.receipt_id IS NOT NULL THEN
        EXECUTE format(
            'SELECT "clientId" AS client_id, "branchId" AS branch_id, amount::numeric AS amount
               FROM %I."ClientReceipts" WHERE "crId" = $1',
            TG_TABLE_SCHEMA)
        INTO rc USING NEW.receipt_id;
        IF rc IS NULL THEN
            RAISE EXCEPTION 'ar_receipt_allocation: receipt % not found', NEW.receipt_id
                USING ERRCODE = 'foreign_key_violation';
        END IF;
        IF rc.client_id <> NEW.client_id THEN
            RAISE EXCEPTION 'ar_receipt_allocation: receipt % belongs to client %, not client %',
                NEW.receipt_id, rc.client_id, NEW.client_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reversal_of_allocation_id IS NULL THEN
            EXECUTE format(
                'SELECT COALESCE(SUM(amount), 0)
                   FROM %I.ar_receipt_allocation
                  WHERE receipt_id = $1 AND status = ''POSTED''
                    AND reversal_of_allocation_id IS NULL',
                TG_TABLE_SCHEMA)
            INTO active_sum USING NEW.receipt_id;
            IF active_sum + NEW.amount > rc.amount THEN
                RAISE EXCEPTION 'ar_receipt_allocation: allocations (% + %) would exceed receipt % amount %',
                    active_sum, NEW.amount, NEW.receipt_id, rc.amount
                    USING ERRCODE = 'check_violation';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ar_receipt_allocation UPDATE/DELETE guard: append-only.
-- The single permitted mutation is the POSTED -> REVERSED status flip
-- (performed by the reversal engine after inserting the mirror row).
CREATE OR REPLACE FUNCTION public.ar_receipt_allocation_mutation_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        RAISE EXCEPTION 'ar_receipt_allocation % is append-only and cannot be deleted (use reversal)',
            OLD.allocation_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF OLD.status = 'POSTED' AND NEW.status = 'REVERSED'
       AND (to_jsonb(NEW) - 'status') = (to_jsonb(OLD) - 'status') THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'ar_receipt_allocation % is immutable (only the POSTED -> REVERSED transition is permitted)',
        OLD.allocation_id
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- Per-tenant structures
-- ---------------------------------------------------------------------
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
        RAISE NOTICE 'Skipping AR open items foundation for %, "Client" table does not exist', target_schema;
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
    -- 2. ar_open_item
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ar_open_item (
            open_item_id       BIGSERIAL PRIMARY KEY,
            company_id         INTEGER NOT NULL,
            branch_id          INTEGER NOT NULL,
            client_id          INTEGER NOT NULL,
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
            CONSTRAINT ar_oi_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT ar_oi_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT ar_oi_client_fk
                FOREIGN KEY (client_id) REFERENCES %I."Client" (c_id) ON DELETE RESTRICT,
            CONSTRAINT ar_oi_reversal_fk
                FOREIGN KEY (reversal_of_open_item_id) REFERENCES %I.ar_open_item (open_item_id),
            CONSTRAINT ar_oi_source_type_ck
                CHECK (source_type IN (''POS_ORDER'', ''OPENING_BALANCE'', ''ADJUSTMENT'')),
            CONSTRAINT ar_oi_status_ck
                CHECK (status IN (''OPEN'', ''PARTIALLY_SETTLED'', ''SETTLED'', ''REVERSED'')),
            CONSTRAINT ar_oi_amounts_nonneg_ck
                CHECK (total_amount >= 0 AND settled_amount >= 0 AND remaining_amount >= 0),
            CONSTRAINT ar_oi_amounts_sum_ck
                CHECK (settled_amount + remaining_amount = total_amount),
            CONSTRAINT ar_oi_status_balance_ck CHECK (
                (status = ''OPEN''              AND settled_amount = 0 AND remaining_amount = total_amount) OR
                (status = ''PARTIALLY_SETTLED'' AND settled_amount > 0 AND remaining_amount > 0) OR
                (status = ''SETTLED''           AND remaining_amount = 0) OR
                (status = ''REVERSED''          AND remaining_amount = 0)
            )
        )
    ', target_schema, target_schema, target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (company_id, branch_id, source_type, source_id)
            WHERE source_id IS NOT NULL
    ', 'ux_' || target_schema || '_ar_oi_source', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (company_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL
    ', 'ux_' || target_schema || '_ar_oi_idempotency', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (client_id, status, due_date)
    ', 'idx_' || target_schema || '_ar_oi_client_status_due', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_open_item (branch_id, document_date DESC)
    ', 'idx_' || target_schema || '_ar_oi_branch_date', target_schema);

    EXECUTE format('DROP TRIGGER IF EXISTS trg_ar_open_item_guard ON %I.ar_open_item', target_schema);
    EXECUTE format('
        CREATE TRIGGER trg_ar_open_item_guard
            BEFORE UPDATE OR DELETE ON %I.ar_open_item
            FOR EACH ROW EXECUTE FUNCTION public.ar_open_item_guard()
    ', target_schema);

    -- ------------------------------------------------------------------
    -- 3. ar_receipt_allocation
    -- ------------------------------------------------------------------
    IF receipts_table IS NULL THEN
        RAISE NOTICE 'Skipping ar_receipt_allocation for %, "ClientReceipts" table does not exist', target_schema;
        RETURN;
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ar_receipt_allocation (
            allocation_id      BIGSERIAL PRIMARY KEY,
            company_id         INTEGER NOT NULL,
            branch_id          INTEGER NOT NULL,
            client_id          INTEGER NOT NULL,
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
            CONSTRAINT ar_alloc_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT ar_alloc_receipt_fk
                FOREIGN KEY (receipt_id) REFERENCES %I."ClientReceipts" ("crId") ON DELETE RESTRICT,
            CONSTRAINT ar_alloc_open_item_fk
                FOREIGN KEY (open_item_id) REFERENCES %I.ar_open_item (open_item_id) ON DELETE RESTRICT,
            CONSTRAINT ar_alloc_reversal_fk
                FOREIGN KEY (reversal_of_allocation_id) REFERENCES %I.ar_receipt_allocation (allocation_id),
            CONSTRAINT ar_alloc_amount_ck CHECK (amount > 0),
            CONSTRAINT ar_alloc_status_ck CHECK (status IN (''POSTED'', ''REVERSED'')),
            CONSTRAINT ar_alloc_idem_uq UNIQUE (company_id, idempotency_key)
        )
    ', target_schema, target_schema, target_schema, target_schema);

    -- One ACTIVE allocation per (receipt, open item); reversal mirrors repeat the pair.
    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ar_receipt_allocation (receipt_id, open_item_id)
            WHERE status = ''POSTED'' AND reversal_of_allocation_id IS NULL
    ', 'ux_' || target_schema || '_ar_alloc_active', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_receipt_allocation (open_item_id)
    ', 'idx_' || target_schema || '_ar_alloc_open_item', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ar_receipt_allocation (receipt_id)
    ', 'idx_' || target_schema || '_ar_alloc_receipt', target_schema);

    EXECUTE format('DROP TRIGGER IF EXISTS trg_ar_alloc_insert_guard ON %I.ar_receipt_allocation', target_schema);
    EXECUTE format('
        CREATE TRIGGER trg_ar_alloc_insert_guard
            BEFORE INSERT ON %I.ar_receipt_allocation
            FOR EACH ROW EXECUTE FUNCTION public.ar_receipt_allocation_insert_guard()
    ', target_schema);

    EXECUTE format('DROP TRIGGER IF EXISTS trg_ar_alloc_mutation_guard ON %I.ar_receipt_allocation', target_schema);
    EXECUTE format('
        CREATE TRIGGER trg_ar_alloc_mutation_guard
            BEFORE UPDATE OR DELETE ON %I.ar_receipt_allocation
            FOR EACH ROW EXECUTE FUNCTION public.ar_receipt_allocation_mutation_guard()
    ', target_schema);
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
            PERFORM public.ensure_ar_open_items_foundation_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
