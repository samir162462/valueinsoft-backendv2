-- ==========================================================
-- V84 - POS Offline Posting MVP
-- ==========================================================
-- Adds MVP posting statuses, posting metadata columns, and
-- tenant indexes. This migration keeps public.pos_* tables as
-- deprecated compatibility artifacts and does not write runtime
-- offline POS data to public.pos_*.
-- ==========================================================

CREATE OR REPLACE FUNCTION public.ensure_offline_sync_posting_mvp_for_tenant(schema_name text)
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
            ADD COLUMN IF NOT EXISTS posting_started_at TIMESTAMPTZ
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS posting_completed_at TIMESTAMPTZ
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS posted_order_id BIGINT
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS finance_posting_request_id UUID
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS finance_journal_entry_id UUID
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS posting_error_code VARCHAR(100)
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD COLUMN IF NOT EXISTS posting_error_message TEXT
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            DROP CONSTRAINT IF EXISTS chk_order_import_status
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_offline_order_import
            ADD CONSTRAINT chk_order_import_status CHECK (status IN (
                ''PENDING'',
                ''PENDING_RETRY'',
                ''PROCESSING'',
                ''READY_FOR_VALIDATION'',
                ''VALIDATING'',
                ''VALIDATED'',
                ''VALIDATION_FAILED'',
                ''POSTING'',
                ''POSTING_FAILED'',
                ''SYNCED'',
                ''FAILED'',
                ''DUPLICATE'',
                ''NEEDS_REVIEW''
            ))
    ', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.pos_offline_order_import (company_id, branch_id, sync_batch_id, status, id)
    ', 'idx_' || schema_name || '_order_import_posting_claim', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.pos_offline_order_import (company_id, branch_id, posted_order_id)
        WHERE posted_order_id IS NOT NULL
    ', 'idx_' || schema_name || '_order_import_posted_order', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.pos_offline_order_import (company_id, branch_id, posting_started_at)
    ', 'idx_' || schema_name || '_order_import_posting_started', schema_name);
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
        PERFORM public.ensure_offline_sync_posting_mvp_for_tenant(schema_rec.schema_name);
    END LOOP;
END $$;
