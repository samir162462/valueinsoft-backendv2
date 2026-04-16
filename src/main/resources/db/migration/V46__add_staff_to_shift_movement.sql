-- ============================================================
-- V46: Add associated_user_id to shift_cash_movement
-- Allows manual drawer adjustments to be linked to company users (staff).
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
            ADD COLUMN IF NOT EXISTS associated_user_id VARCHAR;
        ', schema_rec.schema_name);
    END LOOP;
END $$;
