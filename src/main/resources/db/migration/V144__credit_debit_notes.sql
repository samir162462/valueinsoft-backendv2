-- =====================================================================
-- V144: Credit notes (AR) and debit notes (AP) as OFFSET DOCUMENTS
-- (per tenant schema c_<companyId>).
--
-- Spec: docs/ar-ap-credit-open-items/OPEN_ITEMS_REVISED_SCHEMA_PLAN.md (§4)
-- Review basis: blocker B10 — negative open items corrupt aging and dunning.
--
-- Model:
--   * ar_credit_note / ap_debit_note carry total/applied/unapplied amounts
--     (all >= 0, applied + unapplied = total). An unapplied note is visible
--     as "credit owed back", never as negative overdue debt.
--   * APPLICATION goes through the existing allocation tables: an allocation
--     row now carries EXACTLY ONE source — a receipt OR a note (XOR CHECK).
--   * The V141/V142 insert-guard functions are REPLACED here (CREATE OR
--     REPLACE on the shared public functions) to validate the note source:
--     party coherence + cap against the note's unapplied amount.
--
-- Idempotent; reused by DbCompany tenant bootstrap (Stage 2.1).
-- NOTE: this migration must run AFTER V141/V142 (Flyway ordering guarantees it).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Note guard trigger functions (identity/total immutable, no DELETE)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ar_credit_note_guard()
RETURNS TRIGGER AS $$
DECLARE
    old_j JSONB;
    new_j JSONB;
    mutable_keys TEXT[] := ARRAY[
        'applied_amount', 'unapplied_amount', 'status', 'notes',
        'posting_request_id', 'journal_entry_id',
        'updated_by', 'updated_at', 'version'
    ];
    k TEXT;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        RAISE EXCEPTION 'ar_credit_note % is append-only and cannot be deleted (use reversal)',
            OLD.credit_note_id
            USING ERRCODE = 'check_violation';
    END IF;
    old_j := to_jsonb(OLD);
    new_j := to_jsonb(NEW);
    FOREACH k IN ARRAY mutable_keys LOOP
        old_j := old_j - k;
        new_j := new_j - k;
    END LOOP;
    IF old_j <> new_j THEN
        RAISE EXCEPTION 'ar_credit_note %: only application/status/audit columns may change',
            OLD.credit_note_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.ap_debit_note_guard()
RETURNS TRIGGER AS $$
DECLARE
    old_j JSONB;
    new_j JSONB;
    mutable_keys TEXT[] := ARRAY[
        'applied_amount', 'unapplied_amount', 'status', 'notes',
        'posting_request_id', 'journal_entry_id',
        'updated_by', 'updated_at', 'version'
    ];
    k TEXT;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        RAISE EXCEPTION 'ap_debit_note % is append-only and cannot be deleted (use reversal)',
            OLD.debit_note_id
            USING ERRCODE = 'check_violation';
    END IF;
    old_j := to_jsonb(OLD);
    new_j := to_jsonb(NEW);
    FOREACH k IN ARRAY mutable_keys LOOP
        old_j := old_j - k;
        new_j := new_j - k;
    END LOOP;
    IF old_j <> new_j THEN
        RAISE EXCEPTION 'ap_debit_note %: only application/status/audit columns may change',
            OLD.debit_note_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 2. REPLACE the AR allocation insert guard: receipt OR credit note source
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ar_receipt_allocation_insert_guard()
RETURNS TRIGGER AS $$
DECLARE
    oi RECORD;
    rc RECORD;
    cn RECORD;
    orig RECORD;
    active_sum NUMERIC(19,4);
BEGIN
    IF NEW.status <> 'POSTED' THEN
        RAISE EXCEPTION 'ar_receipt_allocation must be inserted as POSTED'
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
        EXECUTE format(
            'SELECT receipt_id, credit_note_id, open_item_id, amount, status, reversal_of_allocation_id
               FROM %I.ar_receipt_allocation WHERE allocation_id = $1',
            TG_TABLE_SCHEMA)
        INTO orig USING NEW.reversal_of_allocation_id;
        IF orig IS NULL
           OR orig.status <> 'POSTED'
           OR orig.reversal_of_allocation_id IS NOT NULL
           OR orig.receipt_id IS DISTINCT FROM NEW.receipt_id
           OR orig.credit_note_id IS DISTINCT FROM NEW.credit_note_id
           OR orig.open_item_id <> NEW.open_item_id
           OR orig.amount <> NEW.amount THEN
            RAISE EXCEPTION 'ar_receipt_allocation: reversal must mirror an active POSTED allocation exactly (target %)',
                NEW.reversal_of_allocation_id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    -- Source A: client receipt
    IF NEW.receipt_id IS NOT NULL THEN
        EXECUTE format(
            'SELECT "clientId" AS client_id, amount::numeric AS amount, status
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
            IF rc.status IS DISTINCT FROM 'POSTED' THEN
                RAISE EXCEPTION 'ar_receipt_allocation: receipt % is % and cannot be allocated',
                    NEW.receipt_id, rc.status
                    USING ERRCODE = 'check_violation';
            END IF;
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

    -- Source B: credit note
    IF NEW.credit_note_id IS NOT NULL THEN
        EXECUTE format(
            'SELECT company_id, client_id, currency_code, total_amount, status
               FROM %I.ar_credit_note WHERE credit_note_id = $1',
            TG_TABLE_SCHEMA)
        INTO cn USING NEW.credit_note_id;
        IF cn IS NULL THEN
            RAISE EXCEPTION 'ar_receipt_allocation: credit note % not found', NEW.credit_note_id
                USING ERRCODE = 'foreign_key_violation';
        END IF;
        IF cn.company_id <> NEW.company_id OR cn.client_id <> NEW.client_id THEN
            RAISE EXCEPTION 'ar_receipt_allocation: credit note % party mismatch', NEW.credit_note_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF cn.currency_code <> NEW.currency_code THEN
            RAISE EXCEPTION 'ar_receipt_allocation: credit note % currency mismatch', NEW.credit_note_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reversal_of_allocation_id IS NULL THEN
            IF cn.status NOT IN ('OPEN', 'PARTIALLY_APPLIED') THEN
                RAISE EXCEPTION 'ar_receipt_allocation: credit note % is % and cannot be applied',
                    NEW.credit_note_id, cn.status
                    USING ERRCODE = 'check_violation';
            END IF;
            EXECUTE format(
                'SELECT COALESCE(SUM(amount), 0)
                   FROM %I.ar_receipt_allocation
                  WHERE credit_note_id = $1 AND status = ''POSTED''
                    AND reversal_of_allocation_id IS NULL',
                TG_TABLE_SCHEMA)
            INTO active_sum USING NEW.credit_note_id;
            IF active_sum + NEW.amount > cn.total_amount THEN
                RAISE EXCEPTION 'ar_receipt_allocation: applications (% + %) would exceed credit note % total %',
                    active_sum, NEW.amount, NEW.credit_note_id, cn.total_amount
                    USING ERRCODE = 'check_violation';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 3. REPLACE the AP allocation insert guard: receipt OR debit note source
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ap_payment_allocation_insert_guard()
RETURNS TRIGGER AS $$
DECLARE
    oi RECORD;
    rc RECORD;
    dn RECORD;
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
            'SELECT receipt_id, debit_note_id, open_item_id, amount, status, reversal_of_allocation_id
               FROM %I.ap_payment_allocation WHERE allocation_id = $1',
            TG_TABLE_SCHEMA)
        INTO orig USING NEW.reversal_of_allocation_id;
        IF orig IS NULL
           OR orig.status <> 'POSTED'
           OR orig.reversal_of_allocation_id IS NOT NULL
           OR orig.receipt_id IS DISTINCT FROM NEW.receipt_id
           OR orig.debit_note_id IS DISTINCT FROM NEW.debit_note_id
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
                    "amountPaid"::numeric AS amount, status
               FROM %I."supplierReciepts" WHERE "srId" = $1',
            TG_TABLE_SCHEMA)
        INTO rc USING NEW.receipt_id;
        IF rc IS NULL THEN
            RAISE EXCEPTION 'ap_payment_allocation: supplier receipt % not found', NEW.receipt_id
                USING ERRCODE = 'foreign_key_violation';
        END IF;
        IF rc.supplier_id <> NEW.supplier_id OR rc.branch_id <> NEW.branch_id THEN
            RAISE EXCEPTION 'ap_payment_allocation: receipt % party mismatch', NEW.receipt_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reversal_of_allocation_id IS NULL THEN
            IF rc.status IS DISTINCT FROM 'POSTED' THEN
                RAISE EXCEPTION 'ap_payment_allocation: receipt % is % and cannot be allocated',
                    NEW.receipt_id, rc.status
                    USING ERRCODE = 'check_violation';
            END IF;
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

    IF NEW.debit_note_id IS NOT NULL THEN
        EXECUTE format(
            'SELECT company_id, branch_id, supplier_id, currency_code, total_amount, status
               FROM %I.ap_debit_note WHERE debit_note_id = $1',
            TG_TABLE_SCHEMA)
        INTO dn USING NEW.debit_note_id;
        IF dn IS NULL THEN
            RAISE EXCEPTION 'ap_payment_allocation: debit note % not found', NEW.debit_note_id
                USING ERRCODE = 'foreign_key_violation';
        END IF;
        IF dn.company_id <> NEW.company_id
           OR dn.branch_id <> NEW.branch_id
           OR dn.supplier_id <> NEW.supplier_id THEN
            RAISE EXCEPTION 'ap_payment_allocation: debit note % party mismatch', NEW.debit_note_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF dn.currency_code <> NEW.currency_code THEN
            RAISE EXCEPTION 'ap_payment_allocation: debit note % currency mismatch', NEW.debit_note_id
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.reversal_of_allocation_id IS NULL THEN
            IF dn.status NOT IN ('OPEN', 'PARTIALLY_APPLIED') THEN
                RAISE EXCEPTION 'ap_payment_allocation: debit note % is % and cannot be applied',
                    NEW.debit_note_id, dn.status
                    USING ERRCODE = 'check_violation';
            END IF;
            EXECUTE format(
                'SELECT COALESCE(SUM(amount), 0)
                   FROM %I.ap_payment_allocation
                  WHERE debit_note_id = $1 AND status = ''POSTED''
                    AND reversal_of_allocation_id IS NULL',
                TG_TABLE_SCHEMA)
            INTO active_sum USING NEW.debit_note_id;
            IF active_sum + NEW.amount > dn.total_amount THEN
                RAISE EXCEPTION 'ap_payment_allocation: applications (% + %) would exceed debit note % total %',
                    active_sum, NEW.amount, NEW.debit_note_id, dn.total_amount
                    USING ERRCODE = 'check_violation';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 4. Per-tenant structures
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_credit_debit_notes_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    client_table REGCLASS;
    ar_alloc_table REGCLASS;
    ap_alloc_table REGCLASS;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    client_table   := to_regclass(format('%I.%I', target_schema, 'Client'));
    ar_alloc_table := to_regclass(format('%I.%I', target_schema, 'ar_receipt_allocation'));
    ap_alloc_table := to_regclass(format('%I.%I', target_schema, 'ap_payment_allocation'));

    -- ------------------------------------------------------------------
    -- ar_credit_note
    -- ------------------------------------------------------------------
    IF client_table IS NOT NULL THEN
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.ar_credit_note (
                credit_note_id     BIGSERIAL PRIMARY KEY,
                company_id         INTEGER NOT NULL,
                branch_id          INTEGER NOT NULL,
                client_id          INTEGER NOT NULL,
                reason             VARCHAR(255) NOT NULL,
                reference_type     VARCHAR(30),
                reference_id       BIGINT,
                document_date      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                currency_code      VARCHAR(5) NOT NULL,
                total_amount       NUMERIC(19,4) NOT NULL,
                applied_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
                unapplied_amount   NUMERIC(19,4) NOT NULL,
                status             VARCHAR(20) NOT NULL DEFAULT ''OPEN'',
                posting_request_id UUID,
                journal_entry_id   UUID,
                idempotency_key    VARCHAR(160),
                reversal_of_credit_note_id BIGINT,
                notes              VARCHAR(255),
                created_by         VARCHAR(120),
                created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_by         VARCHAR(120),
                updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                version            BIGINT NOT NULL DEFAULT 0,
                CONSTRAINT ar_cn_company_fk
                    FOREIGN KEY (company_id) REFERENCES public."Company" (id),
                CONSTRAINT ar_cn_branch_fk
                    FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
                CONSTRAINT ar_cn_client_fk
                    FOREIGN KEY (client_id) REFERENCES %I."Client" (c_id) ON DELETE RESTRICT,
                CONSTRAINT ar_cn_reversal_fk
                    FOREIGN KEY (reversal_of_credit_note_id) REFERENCES %I.ar_credit_note (credit_note_id),
                CONSTRAINT ar_cn_status_ck
                    CHECK (status IN (''OPEN'', ''PARTIALLY_APPLIED'', ''APPLIED'', ''REVERSED'')),
                CONSTRAINT ar_cn_amounts_nonneg_ck
                    CHECK (total_amount >= 0 AND applied_amount >= 0 AND unapplied_amount >= 0),
                CONSTRAINT ar_cn_amounts_sum_ck
                    CHECK (applied_amount + unapplied_amount = total_amount),
                CONSTRAINT ar_cn_status_balance_ck CHECK (
                    (status = ''OPEN''              AND applied_amount = 0 AND unapplied_amount = total_amount) OR
                    (status = ''PARTIALLY_APPLIED'' AND applied_amount > 0 AND unapplied_amount > 0) OR
                    (status = ''APPLIED''           AND unapplied_amount = 0) OR
                    (status = ''REVERSED''          AND unapplied_amount = 0)
                )
            )
        ', target_schema, target_schema, target_schema);

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I.ar_credit_note (company_id, idempotency_key)
                WHERE idempotency_key IS NOT NULL
        ', 'ux_' || target_schema || '_ar_cn_idempotency', target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.ar_credit_note (client_id, status)
        ', 'idx_' || target_schema || '_ar_cn_client_status', target_schema);

        EXECUTE format('DROP TRIGGER IF EXISTS trg_ar_credit_note_guard ON %I.ar_credit_note', target_schema);
        EXECUTE format('
            CREATE TRIGGER trg_ar_credit_note_guard
                BEFORE UPDATE OR DELETE ON %I.ar_credit_note
                FOR EACH ROW EXECUTE FUNCTION public.ar_credit_note_guard()
        ', target_schema);
    END IF;

    -- ------------------------------------------------------------------
    -- ap_debit_note
    -- ------------------------------------------------------------------
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ap_debit_note (
            debit_note_id      BIGSERIAL PRIMARY KEY,
            company_id         INTEGER NOT NULL,
            branch_id          INTEGER NOT NULL,
            supplier_id        INTEGER NOT NULL,
            reason             VARCHAR(255) NOT NULL,
            reference_type     VARCHAR(30),
            reference_id       BIGINT,
            document_date      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            currency_code      VARCHAR(5) NOT NULL,
            total_amount       NUMERIC(19,4) NOT NULL,
            applied_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
            unapplied_amount   NUMERIC(19,4) NOT NULL,
            status             VARCHAR(20) NOT NULL DEFAULT ''OPEN'',
            posting_request_id UUID,
            journal_entry_id   UUID,
            idempotency_key    VARCHAR(160),
            reversal_of_debit_note_id BIGINT,
            notes              VARCHAR(255),
            created_by         VARCHAR(120),
            created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by         VARCHAR(120),
            updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version            BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT ap_dn_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT ap_dn_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT ap_dn_reversal_fk
                FOREIGN KEY (reversal_of_debit_note_id) REFERENCES %I.ap_debit_note (debit_note_id),
            CONSTRAINT ap_dn_status_ck
                CHECK (status IN (''OPEN'', ''PARTIALLY_APPLIED'', ''APPLIED'', ''REVERSED'')),
            CONSTRAINT ap_dn_amounts_nonneg_ck
                CHECK (total_amount >= 0 AND applied_amount >= 0 AND unapplied_amount >= 0),
            CONSTRAINT ap_dn_amounts_sum_ck
                CHECK (applied_amount + unapplied_amount = total_amount),
            CONSTRAINT ap_dn_status_balance_ck CHECK (
                (status = ''OPEN''              AND applied_amount = 0 AND unapplied_amount = total_amount) OR
                (status = ''PARTIALLY_APPLIED'' AND applied_amount > 0 AND unapplied_amount > 0) OR
                (status = ''APPLIED''           AND unapplied_amount = 0) OR
                (status = ''REVERSED''          AND unapplied_amount = 0)
            )
        )
    ', target_schema, target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.ap_debit_note (company_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL
    ', 'ux_' || target_schema || '_ap_dn_idempotency', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.ap_debit_note (branch_id, supplier_id, status)
    ', 'idx_' || target_schema || '_ap_dn_supplier_status', target_schema);

    EXECUTE format('DROP TRIGGER IF EXISTS trg_ap_debit_note_guard ON %I.ap_debit_note', target_schema);
    EXECUTE format('
        CREATE TRIGGER trg_ap_debit_note_guard
            BEFORE UPDATE OR DELETE ON %I.ap_debit_note
            FOR EACH ROW EXECUTE FUNCTION public.ap_debit_note_guard()
    ', target_schema);

    -- ------------------------------------------------------------------
    -- Allocation source extension: receipt XOR note
    -- ------------------------------------------------------------------
    IF ar_alloc_table IS NOT NULL THEN
        EXECUTE format('ALTER TABLE %I.ar_receipt_allocation ALTER COLUMN receipt_id DROP NOT NULL', target_schema);
        EXECUTE format('
            ALTER TABLE %I.ar_receipt_allocation
                ADD COLUMN IF NOT EXISTS credit_note_id BIGINT
        ', target_schema);
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ar_alloc_table AND conname = 'ar_alloc_credit_note_fk'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.ar_receipt_allocation
                    ADD CONSTRAINT ar_alloc_credit_note_fk
                    FOREIGN KEY (credit_note_id) REFERENCES %I.ar_credit_note (credit_note_id) ON DELETE RESTRICT
            ', target_schema, target_schema);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ar_alloc_table AND conname = 'ar_alloc_source_xor_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.ar_receipt_allocation
                    ADD CONSTRAINT ar_alloc_source_xor_ck
                    CHECK ((receipt_id IS NULL) <> (credit_note_id IS NULL))
            ', target_schema);
        END IF;
        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I.ar_receipt_allocation (credit_note_id, open_item_id)
                WHERE status = ''POSTED'' AND reversal_of_allocation_id IS NULL AND credit_note_id IS NOT NULL
        ', 'ux_' || target_schema || '_ar_alloc_note_active', target_schema);
    END IF;

    IF ap_alloc_table IS NOT NULL THEN
        EXECUTE format('ALTER TABLE %I.ap_payment_allocation ALTER COLUMN receipt_id DROP NOT NULL', target_schema);
        EXECUTE format('
            ALTER TABLE %I.ap_payment_allocation
                ADD COLUMN IF NOT EXISTS debit_note_id BIGINT
        ', target_schema);
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ap_alloc_table AND conname = 'ap_alloc_debit_note_fk'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.ap_payment_allocation
                    ADD CONSTRAINT ap_alloc_debit_note_fk
                    FOREIGN KEY (debit_note_id) REFERENCES %I.ap_debit_note (debit_note_id) ON DELETE RESTRICT
            ', target_schema, target_schema);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = ap_alloc_table AND conname = 'ap_alloc_source_xor_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.ap_payment_allocation
                    ADD CONSTRAINT ap_alloc_source_xor_ck
                    CHECK ((receipt_id IS NULL) <> (debit_note_id IS NULL))
            ', target_schema);
        END IF;
        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I.ap_payment_allocation (debit_note_id, open_item_id)
                WHERE status = ''POSTED'' AND reversal_of_allocation_id IS NULL AND debit_note_id IS NOT NULL
        ', 'ux_' || target_schema || '_ap_alloc_note_active', target_schema);
    END IF;
END;
$$;

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
            PERFORM public.ensure_credit_debit_notes_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
