-- =====================================================================
-- V143: Receipt hardening (per tenant schema c_<companyId>).
--
-- Spec: docs/ar-ap-credit-open-items/OPEN_ITEMS_REVISED_SCHEMA_PLAN.md (§2.4, §3)
--
-- "ClientReceipts" and "supplierReciepts" predate the finance module: no
-- status, no reversal linkage, no idempotency (review F7). The allocation
-- engine (Stage 4) and reversal engine need all three. This migration adds:
--   * status POSTED|REVERSED (default POSTED — grandfathers every existing row)
--   * reversal_of_id — a reversal receipt references the receipt it undoes;
--     the original flips to REVERSED. No receipt row is ever deleted.
--   * idempotency_key — partial unique; NULL for legacy rows.
--   * payment_method on "ClientReceipts" — today the "type" column conflates
--     document kind ('ReceiveVMoney'/'supportExChange') with payment method.
--     New rows record both; legacy rows keep payment_method NULL.
--
-- Existing negative-amount 'supportExChange' rows are intentionally left
-- untouched (grandfathered). New payouts use type='ClientPayout' with a
-- POSITIVE amount and their own GL posting (service change, Stage 4.5) —
-- closing the review F8 gap (negative receipts invisible to the GL) for
-- new data without rewriting history.
--
-- Idempotent; reused by DbCompany tenant bootstrap (Stage 2.1).
-- =====================================================================

CREATE OR REPLACE FUNCTION public.ensure_receipt_hardening_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    client_receipts REGCLASS;
    supplier_receipts REGCLASS;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    client_receipts   := to_regclass(format('%I.%I', target_schema, 'ClientReceipts'));
    supplier_receipts := to_regclass(format('%I.%I', target_schema, 'supplierReciepts'));

    -- ------------------------------------------------------------------
    -- 1. ClientReceipts
    -- ------------------------------------------------------------------
    IF client_receipts IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I."ClientReceipts"
                ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT ''POSTED'',
                ADD COLUMN IF NOT EXISTS reversal_of_id INTEGER,
                ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160),
                ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30)
        ', target_schema);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = client_receipts AND conname = 'client_receipts_status_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I."ClientReceipts"
                    ADD CONSTRAINT client_receipts_status_ck
                    CHECK (status IN (''POSTED'', ''REVERSED''))
                    NOT VALID
            ', target_schema);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = client_receipts AND conname = 'client_receipts_reversal_fk'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I."ClientReceipts"
                    ADD CONSTRAINT client_receipts_reversal_fk
                    FOREIGN KEY (reversal_of_id) REFERENCES %I."ClientReceipts" ("crId")
                    NOT VALID
            ', target_schema, target_schema);
        END IF;

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I."ClientReceipts" (idempotency_key)
                WHERE idempotency_key IS NOT NULL
        ', 'ux_' || target_schema || '_client_receipts_idempotency', target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I."ClientReceipts" ("clientId", status)
        ', 'idx_' || target_schema || '_client_receipts_client_status', target_schema);
    ELSE
        RAISE NOTICE 'Skipping ClientReceipts hardening for %, table does not exist', target_schema;
    END IF;

    -- ------------------------------------------------------------------
    -- 2. supplierReciepts
    -- ------------------------------------------------------------------
    IF supplier_receipts IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I."supplierReciepts"
                ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT ''POSTED'',
                ADD COLUMN IF NOT EXISTS reversal_of_id INTEGER,
                ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160)
        ', target_schema);

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = supplier_receipts AND conname = 'supplier_receipts_status_ck'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I."supplierReciepts"
                    ADD CONSTRAINT supplier_receipts_status_ck
                    CHECK (status IN (''POSTED'', ''REVERSED''))
                    NOT VALID
            ', target_schema);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = supplier_receipts AND conname = 'supplier_receipts_reversal_fk'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I."supplierReciepts"
                    ADD CONSTRAINT supplier_receipts_reversal_fk
                    FOREIGN KEY (reversal_of_id) REFERENCES %I."supplierReciepts" ("srId")
                    NOT VALID
            ', target_schema, target_schema);
        END IF;

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I."supplierReciepts" (idempotency_key)
                WHERE idempotency_key IS NOT NULL
        ', 'ux_' || target_schema || '_supplier_receipts_idempotency', target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I."supplierReciepts" ("supplierId", "branchId", status)
        ', 'idx_' || target_schema || '_supplier_receipts_party_status', target_schema);
    ELSE
        RAISE NOTICE 'Skipping supplierReciepts hardening for %, table does not exist', target_schema;
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
            PERFORM public.ensure_receipt_hardening_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
