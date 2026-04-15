DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company"
    LOOP
        schema_name := 'c_' || company_record.id;

        EXECUTE format('
            ALTER TABLE %I."FixArea"
                ADD COLUMN IF NOT EXISTS imei VARCHAR(20),
                ADD COLUMN IF NOT EXISTS "deviceCondition" TEXT,
                ADD COLUMN IF NOT EXISTS accessories TEXT
        ', schema_name);
    END LOOP;
END $$;
