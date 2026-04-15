DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company"
    LOOP
        schema_name := 'c_' || company_record.id;

        -- 1. Add order_id to FixArea to link to POS orders
        EXECUTE format('
            ALTER TABLE %I."FixArea"
                ADD COLUMN IF NOT EXISTS order_id INTEGER
        ', schema_name);

        -- 2. Create table for parts consumed or attached to a repair ticket before checkout
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.fix_area_parts (
                id SERIAL PRIMARY KEY,
                fa_id INTEGER NOT NULL REFERENCES %I."FixArea"("faId") ON DELETE CASCADE,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL,
                unit_price INTEGER NOT NULL,
                total INTEGER NOT NULL,
                is_deducted BOOLEAN DEFAULT FALSE
            )
        ', schema_name, schema_name);
        
        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_fix_area_parts_fa_id ON %I.fix_area_parts(fa_id);
        ', schema_name);

    END LOOP;
END $$;
