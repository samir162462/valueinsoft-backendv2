-- ============================================================
-- V44: Add shift_id FK to PosOrder tables
-- Updates all tenant PosOrder tables to formally link
-- orders to shifts, instead of inferring via time windows.
-- ============================================================

DO $$
DECLARE
    schema_rec RECORD;
    table_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c_%'
    LOOP
        -- For every PosOrder table in the tenant schema...
        FOR table_rec IN
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = schema_rec.schema_name
              AND table_name LIKE 'PosOrder_%'
        LOOP
            EXECUTE format('
                ALTER TABLE %I.%I
                ADD COLUMN IF NOT EXISTS shift_id INTEGER;
            ', schema_rec.schema_name, table_rec.table_name);

            -- (Future task: After time-based logic is fully deprecated, we can ADD CONSTRAINT
            -- REFERENCES "PosShiftPeriod"("PosSOID") but for now we simply add the column)
            
            EXECUTE format('
                CREATE INDEX IF NOT EXISTS idx_order_shift_id
                ON %I.%I (shift_id);
            ', schema_rec.schema_name, table_rec.table_name);
        END LOOP;
    END LOOP;
END $$;
