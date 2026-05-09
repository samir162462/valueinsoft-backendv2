-- ==========================================================
-- V91 - Inventory Product Import File Storage Metadata
-- ==========================================================
-- Stores S3 object metadata for original upload files and
-- generated error reports so import history can download them.
-- ==========================================================

ALTER FUNCTION public.create_inventory_product_import_tables_for_tenant(text) RENAME TO create_inventory_product_import_tables_for_tenant_v90;

CREATE OR REPLACE FUNCTION public.create_inventory_product_import_tables_for_tenant(schema_name text)
RETURNS void AS $$
BEGIN
    PERFORM public.create_inventory_product_import_tables_for_tenant_v90(schema_name);

    EXECUTE format('
        ALTER TABLE %I.inventory_import_batch
            ADD COLUMN IF NOT EXISTS original_file_key VARCHAR(500),
            ADD COLUMN IF NOT EXISTS original_file_size BIGINT,
            ADD COLUMN IF NOT EXISTS original_content_type VARCHAR(120),
            ADD COLUMN IF NOT EXISTS original_uploaded_at TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS error_report_file_key VARCHAR(500),
            ADD COLUMN IF NOT EXISTS error_report_file_size BIGINT,
            ADD COLUMN IF NOT EXISTS error_report_generated_at TIMESTAMPTZ
    ', schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_import_batch (company_id, branch_id, updated_at DESC)',
        'idx_' || schema_name || '_inventory_import_batch_scope_updated', schema_name);
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
        ORDER BY schema_name
    LOOP
        PERFORM public.create_inventory_product_import_tables_for_tenant(schema_rec.schema_name);
    END LOOP;
END $$;
