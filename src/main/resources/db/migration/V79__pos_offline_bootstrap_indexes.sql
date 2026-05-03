-- ==========================================================
-- V79 - POS Offline Bootstrap Lookup Indexes
-- ==========================================================
-- Supports cursor-paginated branch bootstrap reads without
-- loading full product catalogs into memory.
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
            CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_product (product_id, updated_at)
        ', 'idx_' || schema_rec.schema_name || '_offline_bootstrap_product_cursor', schema_rec.schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_branch_stock_balance (branch_id, product_id)
        ', 'idx_' || schema_rec.schema_name || '_offline_bootstrap_stock_branch_product', schema_rec.schema_name);
    END LOOP;
END $$;
