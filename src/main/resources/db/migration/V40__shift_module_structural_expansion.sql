-- ============================================================
-- V40: Shift Module Structural Expansion
-- Adds cashier tracking, status lifecycle, opening float,
-- cash reconciliation, and close summary columns to
-- PosShiftPeriod for every existing tenant schema.
-- ============================================================

DO $$
DECLARE
    schema_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c_%'
    LOOP
        -- ── new columns ─────────────────────────────────────
        EXECUTE format('
            ALTER TABLE %I."PosShiftPeriod"
                ADD COLUMN IF NOT EXISTS opened_by_user_id   VARCHAR(120),
                ADD COLUMN IF NOT EXISTS assigned_cashier_id  VARCHAR(120),
                ADD COLUMN IF NOT EXISTS closed_by_user_id    VARCHAR(120),
                ADD COLUMN IF NOT EXISTS register_code        VARCHAR(40),
                ADD COLUMN IF NOT EXISTS status               VARCHAR(20) NOT NULL DEFAULT ''OPEN'',
                ADD COLUMN IF NOT EXISTS opening_float        NUMERIC(14,2) NOT NULL DEFAULT 0,
                ADD COLUMN IF NOT EXISTS expected_cash        NUMERIC(14,2),
                ADD COLUMN IF NOT EXISTS counted_cash         NUMERIC(14,2),
                ADD COLUMN IF NOT EXISTS variance_amount      NUMERIC(14,2),
                ADD COLUMN IF NOT EXISTS variance_reason      VARCHAR(500),
                ADD COLUMN IF NOT EXISTS close_note           VARCHAR(500),
                ADD COLUMN IF NOT EXISTS order_count          INTEGER DEFAULT 0,
                ADD COLUMN IF NOT EXISTS gross_sales          NUMERIC(14,2) DEFAULT 0,
                ADD COLUMN IF NOT EXISTS net_sales            NUMERIC(14,2) DEFAULT 0,
                ADD COLUMN IF NOT EXISTS discount_total       NUMERIC(14,2) DEFAULT 0,
                ADD COLUMN IF NOT EXISTS refund_total         NUMERIC(14,2) DEFAULT 0,
                ADD COLUMN IF NOT EXISTS version              INTEGER NOT NULL DEFAULT 1,
                ADD COLUMN IF NOT EXISTS created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                ADD COLUMN IF NOT EXISTS updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ', schema_rec.schema_name);

        -- ── backfill status for already-closed rows ─────────
        EXECUTE format('
            UPDATE %I."PosShiftPeriod"
            SET status = ''CLOSED''
            WHERE "ShiftEndTime" IS NOT NULL
              AND status = ''OPEN''
        ', schema_rec.schema_name);

        -- ── check constraint on status ──────────────────────
        EXECUTE format('
            ALTER TABLE %I."PosShiftPeriod"
                DROP CONSTRAINT IF EXISTS shift_status_ck
        ', schema_rec.schema_name);

        EXECUTE format('
            ALTER TABLE %I."PosShiftPeriod"
                ADD CONSTRAINT shift_status_ck
                CHECK (status IN (''OPEN'', ''CLOSING'', ''CLOSED'', ''FORCE_CLOSED''))
        ', schema_rec.schema_name);

        -- ── indexes ─────────────────────────────────────────
        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_shift_branch_status
                ON %I."PosShiftPeriod" ("branchId", status)
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_shift_opened_at
                ON %I."PosShiftPeriod" ("ShiftStartTime" DESC)
        ', schema_rec.schema_name);
    END LOOP;
END $$;
