CREATE OR REPLACE FUNCTION public.ensure_serialized_inventory_unit_costing_for_tenant(target_schema TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    unit_table REGCLASS;
    ledger_table REGCLASS;
    product_table REGCLASS;
    legacy_table REGCLASS;
    branch_record RECORD;
    constraint_name TEXT := 'inventory_product_unit_acquisition_cost_ck';
    imei_luhn_constraint_definition TEXT;
    canonical_identifier_constraint_definition TEXT;
BEGIN
    IF target_schema IS NULL OR target_schema !~ '^c_[0-9]+$' THEN
        RAISE EXCEPTION 'Invalid tenant schema: %', target_schema;
    END IF;

    unit_table := to_regclass(format('%I.%I', target_schema, 'inventory_product_unit'));
    IF unit_table IS NULL THEN
        RAISE NOTICE 'Skipping serialized unit costing for %, inventory_product_unit does not exist', target_schema;
        RETURN;
    END IF;

    ledger_table := to_regclass(format('%I.%I', target_schema, 'inventory_stock_ledger'));
    product_table := to_regclass(format('%I.%I', target_schema, 'inventory_product'));

    -- V137 intentionally installed the IMEI checks as NOT VALID so historical
    -- identifiers could remain while every new write is checked. PostgreSQL also
    -- evaluates a NOT VALID check when an existing row is updated, even when the
    -- changed column is unrelated. Preserve and temporarily remove only those
    -- unvalidated checks while acquisition_cost is backfilled, then restore them.
    SELECT pg_get_constraintdef(oid)
    INTO imei_luhn_constraint_definition
    FROM pg_constraint
    WHERE conrelid = unit_table
      AND conname = 'inventory_product_unit_imei_luhn_ck'
      AND NOT convalidated;

    SELECT pg_get_constraintdef(oid)
    INTO canonical_identifier_constraint_definition
    FROM pg_constraint
    WHERE conrelid = unit_table
      AND conname = 'inventory_product_unit_canonical_identifier_ck'
      AND NOT convalidated;

    IF imei_luhn_constraint_definition IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_product_unit DROP CONSTRAINT inventory_product_unit_imei_luhn_ck',
            target_schema
        );
    END IF;

    IF canonical_identifier_constraint_definition IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_product_unit DROP CONSTRAINT inventory_product_unit_canonical_identifier_ck',
            target_schema
        );
    END IF;

    EXECUTE format('
        ALTER TABLE %I.inventory_product_unit
            ADD COLUMN IF NOT EXISTS acquisition_cost NUMERIC(19,4)
    ', target_schema);

    -- Product receipts already contain the exact receipt total and quantity.
    -- Backfill each serialized unit from the receipt it belongs to when possible.
    IF ledger_table IS NOT NULL THEN
        EXECUTE format('
            UPDATE %I.inventory_product_unit unit
            SET acquisition_cost = (
                SELECT ROUND(
                    ledger.trans_total::numeric / NULLIF(ABS(ledger.quantity_delta), 0),
                    4
                )
                FROM %I.inventory_stock_ledger ledger
                WHERE ledger.product_id = unit.product_id
                  AND ledger.branch_id = unit.branch_id
                  AND ledger.reference_type = ''PRODUCT_RECEIPT''
                  AND ledger.reference_id = unit.purchase_reference_id
                  AND ledger.quantity_delta <> 0
                ORDER BY ledger.created_at DESC, ledger.stock_ledger_id DESC
                LIMIT 1
            )
            WHERE unit.acquisition_cost IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM %I.inventory_stock_ledger ledger
                  WHERE ledger.product_id = unit.product_id
                    AND ledger.branch_id = unit.branch_id
                    AND ledger.reference_type = ''PRODUCT_RECEIPT''
                    AND ledger.reference_id = unit.purchase_reference_id
                    AND ledger.quantity_delta <> 0
              )
        ', target_schema, target_schema, target_schema);
    END IF;

    -- Serialized units received through the legacy inventory transaction endpoint
    -- carry that transaction id. Preserve its exact per-unit purchase cost too.
    FOR branch_record IN EXECUTE format(
        'SELECT DISTINCT branch_id FROM %I.inventory_product_unit WHERE acquisition_cost IS NULL',
        target_schema
    ) LOOP
        legacy_table := to_regclass(format(
            '%I.%I',
            target_schema,
            'InventoryTransactions_' || branch_record.branch_id
        ));

        IF legacy_table IS NOT NULL THEN
            EXECUTE format('
                UPDATE %I.inventory_product_unit unit
                SET acquisition_cost = ROUND(
                    legacy_tx."transTotal"::numeric / NULLIF(ABS(legacy_tx."NumItems"), 0),
                    4
                )
                FROM %I.%I legacy_tx
                WHERE unit.branch_id = %L
                  AND UPPER(COALESCE(unit.purchase_reference_type, '''')) = ''INVENTORY_TRANSACTION''
                  AND unit.purchase_reference_id = legacy_tx."transId"::text
                  AND legacy_tx."NumItems" <> 0
                  AND unit.acquisition_cost IS NULL
            ', target_schema, target_schema,
               'InventoryTransactions_' || branch_record.branch_id,
               branch_record.branch_id);
        END IF;
    END LOOP;

    -- Legacy serialized rows may predate receipt references. The product master
    -- cost is the safest available historical fallback for those units only.
    IF product_table IS NOT NULL THEN
        EXECUTE format('
            UPDATE %I.inventory_product_unit unit
            SET acquisition_cost = COALESCE(product.buying_price, 0)::numeric
            FROM %I.inventory_product product
            WHERE product.product_id = unit.product_id
              AND unit.acquisition_cost IS NULL
        ', target_schema, target_schema);
    END IF;

    EXECUTE format('
        UPDATE %I.inventory_product_unit
        SET acquisition_cost = 0
        WHERE acquisition_cost IS NULL
    ', target_schema);

    EXECUTE format('
        ALTER TABLE %I.inventory_product_unit
            ALTER COLUMN acquisition_cost SET DEFAULT 0,
            ALTER COLUMN acquisition_cost SET NOT NULL
    ', target_schema);

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = constraint_name
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT %I CHECK (acquisition_cost >= 0) NOT VALID
        ', target_schema, constraint_name);
    END IF;

    IF imei_luhn_constraint_definition IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_product_unit ADD CONSTRAINT inventory_product_unit_imei_luhn_ck %s',
            target_schema,
            imei_luhn_constraint_definition
        );
    END IF;

    IF canonical_identifier_constraint_definition IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_product_unit ADD CONSTRAINT inventory_product_unit_canonical_identifier_ck %s',
            target_schema,
            canonical_identifier_constraint_definition
        );
    END IF;
END;
$$;

DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company" WHERE id > 0 LOOP
        schema_name := 'c_' || company_record.id;
        IF to_regnamespace(schema_name) IS NOT NULL THEN
            PERFORM public.ensure_serialized_inventory_unit_costing_for_tenant(schema_name);
        END IF;
    END LOOP;
END;
$$;
