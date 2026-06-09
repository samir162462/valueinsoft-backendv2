-- ==========================================================
-- V104 - Inventory USD Purchase Rate
-- ==========================================================
-- Stores the USD/EGP exchange rate that applied when the
-- product was bought. This is separate from the daily current
-- effective pricing rate.
-- ==========================================================

CREATE OR REPLACE FUNCTION public.add_inventory_usd_purchase_rate_for_tenant(schema_name text)
RETURNS void AS $$
BEGIN
    IF schema_name IS NULL OR schema_name NOT LIKE 'c\_%' ESCAPE '\' THEN
        RAISE EXCEPTION 'Inventory FX tenant schema must start with c_: %', schema_name;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = schema_name
          AND table_name = 'inventory_product'
    ) THEN
        RETURN;
    END IF;

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            ADD COLUMN IF NOT EXISTS purchase_usd_rate NUMERIC(19,8)
    ', schema_name);

    EXECUTE format('
        UPDATE %I.inventory_product
        SET purchase_usd_rate = ROUND((buying_price::numeric / replacement_cost_usd), 8)
        WHERE purchase_usd_rate IS NULL
          AND replacement_cost_usd IS NOT NULL
          AND replacement_cost_usd > 0
          AND buying_price IS NOT NULL
          AND buying_price > 0
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            DROP CONSTRAINT IF EXISTS inventory_product_purchase_usd_rate_ck,
            ADD CONSTRAINT inventory_product_purchase_usd_rate_ck CHECK (
                purchase_usd_rate IS NULL OR purchase_usd_rate > 0
            )
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
        ORDER BY schema_name
    LOOP
        PERFORM public.add_inventory_usd_purchase_rate_for_tenant(schema_rec.schema_name);
    END LOOP;
END $$;
