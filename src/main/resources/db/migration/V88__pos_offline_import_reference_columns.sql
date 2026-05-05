-- ==========================================================
-- V88 - POS Offline Import Reference Columns
-- ==========================================================

CREATE OR REPLACE FUNCTION public.add_pos_offline_import_reference_columns(schema_name text)
RETURNS void AS $$
BEGIN
    -- 1. Add new columns
    EXECUTE format('ALTER TABLE %I.pos_offline_order_import ADD COLUMN IF NOT EXISTS local_order_id VARCHAR(150)', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_offline_order_import ADD COLUMN IF NOT EXISTS device_code VARCHAR(150)', schema_name);
    EXECUTE format('ALTER TABLE %I.pos_offline_order_import ADD COLUMN IF NOT EXISTS client_created_at TIMESTAMPTZ', schema_name);

    -- 2. Relax cashier_id constraint to NULL if compatible
    -- It was NOT NULL in V77.
    EXECUTE format('ALTER TABLE %I.pos_offline_order_import ALTER COLUMN cashier_id DROP NOT NULL', schema_name);

    -- 3. Indexes
    -- (company_id, branch_id, batch_id, local_order_id)
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.pos_offline_order_import (company_id, branch_id, sync_batch_id, local_order_id)', 
        'idx_order_import_batch_local_id', schema_name);
    
    -- (company_id, branch_id, device_code, local_order_id)
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.pos_offline_order_import (company_id, branch_id, device_code, local_order_id)', 
        'idx_order_import_device_local_id', schema_name);

END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c\_%' ESCAPE '\'
    LOOP
        PERFORM public.add_pos_offline_import_reference_columns(r.schema_name);
    END LOOP;
END $$;
