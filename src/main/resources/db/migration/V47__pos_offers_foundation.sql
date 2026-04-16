DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN
        SELECT id
        FROM public."Company"
        ORDER BY id
    LOOP
        schema_name := format('c_%s', company_record.id);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.pos_offers (
                offer_id SERIAL PRIMARY KEY,
                branch_id INTEGER NOT NULL,
                offer_name VARCHAR(100) NOT NULL,
                offer_description TEXT,
                offer_type VARCHAR(20) NOT NULL, -- PERCENTAGE, FIXED, BOGO
                offer_value NUMERIC(18,2) DEFAULT 0,
                min_order_total NUMERIC(18,2) DEFAULT 0,
                applicable_items JSONB DEFAULT ''{}''::jsonb, -- {"product_ids": [], "category_ids": []}
                min_quantity INTEGER DEFAULT 1,
                is_active BOOLEAN DEFAULT TRUE,
                start_date TIMESTAMP,
                end_date TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )', schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_pos_offers_branch ON %I.pos_offers (branch_id)', schema_name);
            
        EXECUTE format('
            CREATE INDEX IF NOT EXISTS idx_pos_offers_active_dates ON %I.pos_offers (is_active, start_date, end_date)', schema_name);

    END LOOP;
END $$;
