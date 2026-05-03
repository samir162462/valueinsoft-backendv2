-- ==========================================================
-- V80 - POS Offline Retry Baseline
-- ==========================================================
-- Adds a retry-safe import status and retry timestamp for
-- tenant-scoped offline order imports. No posting logic is added.
-- ==========================================================

DO $$
DECLARE
    schema_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c\_%' ESCAPE '\'
    LOOP
        EXECUTE format('
            ALTER TABLE %I.pos_offline_order_import
                ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMPTZ
        ', schema_rec.schema_name);

        EXECUTE format('
            ALTER TABLE %I.pos_offline_order_import
                DROP CONSTRAINT IF EXISTS chk_order_import_status
        ', schema_rec.schema_name);

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
                    ''SYNCED'',
                    ''FAILED'',
                    ''DUPLICATE'',
                    ''NEEDS_REVIEW''
                ))
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
            ON %I.pos_offline_order_import (company_id, branch_id, status, updated_at)
        ', 'idx_' || schema_rec.schema_name || '_order_import_retry_status', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
            ON %I.pos_offline_order_error (company_id, branch_id, offline_order_import_id, created_at)
        ', 'idx_' || schema_rec.schema_name || '_order_error_import_created', schema_rec.schema_name);
    END LOOP;
END $$;
