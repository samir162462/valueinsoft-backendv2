DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company"
    LOOP
        schema_name := 'c_' || company_record.id;

        EXECUTE format('
            ALTER TABLE %I.inventory_stock_ledger
                ADD COLUMN IF NOT EXISTS supplier_id INTEGER NOT NULL DEFAULT 0,
                ADD COLUMN IF NOT EXISTS trans_total INTEGER NOT NULL DEFAULT 0,
                ADD COLUMN IF NOT EXISTS pay_type VARCHAR(30),
                ADD COLUMN IF NOT EXISTS remaining_amount INTEGER NOT NULL DEFAULT 0
        ', schema_name);
    END LOOP;
END $$;
