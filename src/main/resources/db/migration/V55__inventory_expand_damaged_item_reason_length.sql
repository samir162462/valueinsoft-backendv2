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

        -- Check if table exists in this schema before trying to alter it
        IF EXISTS (
            SELECT 1 
            FROM information_schema.tables 
            WHERE table_schema = schema_name 
            AND table_name = 'DamagedList'
        ) THEN
            EXECUTE format('ALTER TABLE %I."DamagedList" ALTER COLUMN "Reason" TYPE VARCHAR(255)', schema_name);
        END IF;
    END LOOP;
END $$;
