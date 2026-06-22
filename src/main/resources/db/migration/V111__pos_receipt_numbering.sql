CREATE OR REPLACE FUNCTION public.create_pos_receipt_sequences_tables_for_tenant(target_schema TEXT, target_company_id INTEGER)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    order_table RECORD;
    schema_quoted TEXT;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;

    schema_quoted := format('%I', target_schema);

    -- 1. Create the pos_receipt_sequences table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %s.pos_receipt_sequences (
            branch_id BIGINT NOT NULL,
            period_yymm CHAR(4) NOT NULL,
            last_sequence_no INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

            PRIMARY KEY (branch_id, period_yymm),
            CHECK (last_sequence_no BETWEEN 0 AND 999999)
        )
    ', schema_quoted);

    -- 2. Alter all existing PosOrder tables
    FOR order_table IN 
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = target_schema 
        AND table_name LIKE 'PosOrder_%'
        AND table_name NOT LIKE '%_seq'
    LOOP
        -- Add columns
        EXECUTE format('
            ALTER TABLE %s.%I
                ADD COLUMN IF NOT EXISTS receipt_number VARCHAR(14),
                ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255)
        ', schema_quoted, order_table.table_name);

        -- Add indexes and checks
        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I 
            ON %s.%I (receipt_number) 
            WHERE receipt_number IS NOT NULL
        ', 'idx_' || order_table.table_name || '_receipt', schema_quoted, order_table.table_name);

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I 
            ON %s.%I (idempotency_key) 
            WHERE idempotency_key IS NOT NULL
        ', 'idx_' || order_table.table_name || '_idemp', schema_quoted, order_table.table_name);

        EXECUTE format('
            ALTER TABLE %s.%I
            DROP CONSTRAINT IF EXISTS check_receipt_number_format
        ', schema_quoted, order_table.table_name);

        EXECUTE format('
            ALTER TABLE %s.%I
            ADD CONSTRAINT check_receipt_number_format CHECK (receipt_number IS NULL OR receipt_number ~ ''^[0-9]{14}$'')
        ', schema_quoted, order_table.table_name);
    END LOOP;
END;
$$;

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
        IF to_regnamespace(schema_name) IS NOT NULL THEN
            PERFORM public.create_pos_receipt_sequences_tables_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
