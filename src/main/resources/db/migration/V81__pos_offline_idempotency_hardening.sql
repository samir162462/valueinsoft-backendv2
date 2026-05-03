-- ==========================================================
-- V81 - POS Offline Idempotency Hardening
-- ==========================================================
-- Hardens tenant-scoped idempotency status semantics and lookup
-- indexes. The tenant table uses device_id because device_code is
-- stored on pos_device, not pos_idempotency_key.
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
            ALTER TABLE %I.pos_idempotency_key
                ALTER COLUMN status SET DEFAULT ''RECEIVED''
        ', schema_rec.schema_name);

        EXECUTE format('
            ALTER TABLE %I.pos_idempotency_key
                DROP CONSTRAINT IF EXISTS chk_idempotency_status
        ', schema_rec.schema_name);

        EXECUTE format('
            ALTER TABLE %I.pos_idempotency_key
                ADD CONSTRAINT chk_idempotency_status CHECK (status IN (
                    ''RECEIVED'',
                    ''PROCESSING'',
                    ''SYNCED'',
                    ''FAILED'',
                    ''DUPLICATE'',
                    ''NEEDS_REVIEW'',
                    ''PAYLOAD_MISMATCH''
                ))
        ', schema_rec.schema_name);

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.pos_idempotency_key (company_id, branch_id, device_id, idempotency_key)
        ', 'idx_' || schema_rec.schema_name || '_idempotency_scope_key', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
            ON %I.pos_idempotency_key (company_id, branch_id, device_id, idempotency_key, request_hash)
        ', 'idx_' || schema_rec.schema_name || '_idempotency_hash_lookup', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
            ON %I.pos_offline_order_import (company_id, branch_id, device_id, idempotency_key, payload_hash)
        ', 'idx_' || schema_rec.schema_name || '_order_import_idempotency_hash', schema_rec.schema_name);
    END LOOP;
END $$;
