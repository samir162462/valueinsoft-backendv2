CREATE OR REPLACE FUNCTION public.ensure_inventory_damage_foundation_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    expected_schema TEXT := format('c_%s', target_company_id);
    damaged_table REGCLASS;
BEGIN
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;
    IF target_schema IS NULL OR target_schema <> expected_schema THEN
        RAISE EXCEPTION 'target_schema must equal %', expected_schema;
    END IF;

    damaged_table := to_regclass(format('%I."DamagedList"', target_schema));
    IF damaged_table IS NULL THEN
        RAISE EXCEPTION 'Tenant schema % is missing DamagedList', target_schema;
    END IF;

    EXECUTE format('
        ALTER TABLE %I."DamagedList"
            ADD COLUMN IF NOT EXISTS "OperationId" VARCHAR(80),
            ADD COLUMN IF NOT EXISTS "Status" VARCHAR(20) NOT NULL DEFAULT ''POSTED'',
            ADD COLUMN IF NOT EXISTS "UnitCost" INTEGER,
            ADD COLUMN IF NOT EXISTS "InventoryValue" INTEGER,
            ADD COLUMN IF NOT EXISTS "BalanceVersionAfter" BIGINT,
            ADD COLUMN IF NOT EXISTS "ReversedAt" TIMESTAMP,
            ADD COLUMN IF NOT EXISTS "ReversedBy" VARCHAR(120),
            ADD COLUMN IF NOT EXISTS "ReversalReason" VARCHAR(255),
            ADD COLUMN IF NOT EXISTS "ReversalOperationId" VARCHAR(80)
    ', target_schema);

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = damaged_table
          AND conname = 'damaged_list_status_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I."DamagedList"
            ADD CONSTRAINT damaged_list_status_ck
            CHECK ("Status" IN (''POSTED'', ''REVERSED''))
        ', target_schema);
    END IF;

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
        ON %I."DamagedList" ("OperationId")
        WHERE "OperationId" IS NOT NULL
    ', 'ux_' || target_schema || '_damaged_list_operation', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
        ON %I."DamagedList" ("ReversalOperationId")
        WHERE "ReversalOperationId" IS NOT NULL
    ', 'ux_' || target_schema || '_damaged_list_reversal_operation', target_schema);

    INSERT INTO public.inventory_tenant_schema_version (
        company_id, schema_name, inventory_foundation_version, verified_at
    ) VALUES (
        target_company_id, target_schema, 150, NOW()
    )
    ON CONFLICT (company_id) DO UPDATE
    SET schema_name = EXCLUDED.schema_name,
        inventory_foundation_version = EXCLUDED.inventory_foundation_version,
        verified_at = EXCLUDED.verified_at;
END;
$$;

DO $$
DECLARE
    company_record RECORD;
    target_schema TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company" ORDER BY id LOOP
        target_schema := format('c_%s', company_record.id);
        IF to_regnamespace(target_schema) IS NOT NULL
           AND to_regclass(format('%I."DamagedList"', target_schema)) IS NOT NULL THEN
            PERFORM public.ensure_inventory_damage_foundation_for_tenant(target_schema, company_record.id);
        END IF;
    END LOOP;
END $$;
