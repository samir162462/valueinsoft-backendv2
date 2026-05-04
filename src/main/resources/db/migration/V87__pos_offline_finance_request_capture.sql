-- ==========================================================
-- V87 - POS Offline Finance Request Capture
-- ==========================================================
-- Adds lightweight tenant metadata for after-commit finance
-- enqueue visibility. This migration keeps public.pos_* tables
-- as deprecated compatibility artifacts and does not write
-- runtime offline POS data to public.pos_*.
-- ==========================================================

CREATE OR REPLACE FUNCTION public.ensure_offline_sync_finance_capture_for_tenant(schema_name text)
RETURNS void AS $$
BEGIN
    IF schema_name IS NULL OR schema_name NOT LIKE 'c\_%' ESCAPE '\' THEN
        RAISE EXCEPTION 'Offline sync tenant schema must start with c_: %', schema_name;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.schemata
        WHERE schemata.schema_name = $1
    ) THEN
        RAISE EXCEPTION 'Offline sync tenant schema does not exist: %', schema_name;
    END IF;

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS finance_enqueue_status VARCHAR(30)
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS finance_enqueue_error TEXT
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            DROP CONSTRAINT IF EXISTS chk_order_import_finance_enqueue_status
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD CONSTRAINT chk_order_import_finance_enqueue_status CHECK (
                finance_enqueue_status IS NULL OR finance_enqueue_status IN (
                    ''ENQUEUED'',
                    ''UNAVAILABLE'',
                    ''ENQUEUE_FAILED''
                )
            )
    ', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.pos_offline_order_import (company_id, branch_id, finance_posting_request_id)
        WHERE finance_posting_request_id IS NOT NULL
    ', 'idx_' || schema_name || '_order_import_finance_request', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_idempotency_key
            ADD COLUMN IF NOT EXISTS result_metadata JSONB
    ', schema_name);
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    schema_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c\_%' ESCAPE '\'
    LOOP
        PERFORM public.ensure_offline_sync_finance_capture_for_tenant(schema_rec.schema_name);
    END LOOP;
END $$;
