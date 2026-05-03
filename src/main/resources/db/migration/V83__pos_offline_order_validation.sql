-- ==========================================================
-- V83 - POS Offline Real Order Validation
-- ==========================================================
-- Adds validation-only statuses and indexes for tenant offline
-- imports. This migration does not create invoices, payments,
-- inventory movements, or finance postings.
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
            ON %I.pos_offline_order_import (company_id, branch_id, sync_batch_id, status, id)
        ', 'idx_' || schema_rec.schema_name || '_order_import_validation_claim', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
            ON %I.pos_offline_order_error (company_id, branch_id, offline_order_import_id, error_code, created_at)
        ', 'idx_' || schema_rec.schema_name || '_order_error_validation_lookup', schema_rec.schema_name);
    END LOOP;
END $$;
