DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT
            ns.nspname AS schema_name,
            cls.relname AS table_name,
            con.conname AS constraint_name
        FROM pg_constraint con
        JOIN pg_class cls ON cls.oid = con.conrelid
        JOIN pg_namespace ns ON ns.oid = cls.relnamespace
        WHERE con.contype = 'f'
          AND cls.relname LIKE 'InventoryTransactions_%'
          AND pg_get_constraintdef(con.oid) ILIKE '%PosProduct_%'
    LOOP
        EXECUTE format(
                'ALTER TABLE %I.%I DROP CONSTRAINT IF EXISTS %I',
                constraint_record.schema_name,
                constraint_record.table_name,
                constraint_record.constraint_name
        );
    END LOOP;
END
$$;
