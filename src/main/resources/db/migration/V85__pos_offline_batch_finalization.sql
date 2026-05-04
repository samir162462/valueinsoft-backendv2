-- ==========================================================
-- V85 - POS Offline Batch Finalization
-- ==========================================================
-- Adds expanded tenant batch counters and batch lifecycle
-- statuses. This migration does not write runtime offline POS
-- data to public.pos_* tables.
-- ==========================================================

CREATE OR REPLACE FUNCTION public.ensure_offline_sync_batch_finalization_for_tenant(schema_name text)
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

    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS pending_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS pending_retry_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS processing_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS ready_for_validation_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS validating_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS validated_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS posting_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS posting_failed_orders INTEGER NOT NULL DEFAULT 0', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_sync_batch ADD COLUMN IF NOT EXISTS validation_failed_orders INTEGER NOT NULL DEFAULT 0', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_sync_batch
            DROP CONSTRAINT IF EXISTS chk_sync_batch_status
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.pos_sync_batch
            ADD CONSTRAINT chk_sync_batch_status CHECK (status IN (
                ''RECEIVED'',
                ''IN_PROGRESS'',
                ''COMPLETED'',
                ''COMPLETED_WITH_ERRORS'',
                ''FAILED'',
                ''PROCESSING'',
                ''PARTIALLY_SYNCED'',
                ''SYNCED''
            ))
    ', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.pos_sync_batch (company_id, branch_id, status, updated_at)
    ', 'idx_' || schema_name || '_sync_batch_status_updated', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.pos_offline_order_import (company_id, branch_id, sync_batch_id, status, updated_at)
    ', 'idx_' || schema_name || '_order_import_batch_status_updated', schema_name);
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
        PERFORM public.ensure_offline_sync_batch_finalization_for_tenant(schema_rec.schema_name);
    END LOOP;
END $$;
