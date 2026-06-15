-- ============================================================
-- V108: Ensure client_id, associated_user_id, and constraints exist on shift_cash_movement for all tenant schemas
-- ============================================================

DO $$
DECLARE
    schema_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c_%'
    LOOP
        -- Check if shift_cash_movement table exists in this schema
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = schema_rec.schema_name 
            AND table_name = 'shift_cash_movement'
        ) THEN
            -- Add missing columns safely
            EXECUTE format('
                ALTER TABLE %I.shift_cash_movement 
                ADD COLUMN IF NOT EXISTS client_id INTEGER;

                ALTER TABLE %I.shift_cash_movement 
                ADD COLUMN IF NOT EXISTS associated_user_id VARCHAR;
            ', schema_rec.schema_name, schema_rec.schema_name);

            -- Check if Client table exists to define the foreign key constraint
            IF EXISTS (
                SELECT 1 FROM information_schema.tables 
                WHERE table_schema = schema_rec.schema_name 
                AND table_name = 'Client'
            ) THEN
                EXECUTE format('
                    DO $inner$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint 
                            WHERE conname = ''shift_cash_movement_client_fk'' 
                            AND conrelid = %L::regclass
                        ) THEN
                            ALTER TABLE %I.shift_cash_movement
                            ADD CONSTRAINT shift_cash_movement_client_fk
                            FOREIGN KEY (client_id)
                            REFERENCES %I."Client" (c_id)
                            ON DELETE SET NULL;
                        END IF;
                    END $inner$;
                ', schema_rec.schema_name || '.shift_cash_movement',
                   schema_rec.schema_name, 
                   schema_rec.schema_name);
            END IF;
        END IF;
    END LOOP;
END $$;
