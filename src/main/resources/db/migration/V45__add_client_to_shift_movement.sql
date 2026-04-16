-- ============================================================
-- V45: Add client_id to shift_cash_movement
-- Allows manual drawer adjustments to be linked to customers.
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
        EXECUTE format('
            ALTER TABLE %I.shift_cash_movement 
            ADD COLUMN IF NOT EXISTS client_id INTEGER;

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
        ', schema_rec.schema_name, 
           schema_rec.schema_name || '.shift_cash_movement',
           schema_rec.schema_name, 
           schema_rec.schema_name);
    END LOOP;
END $$;
