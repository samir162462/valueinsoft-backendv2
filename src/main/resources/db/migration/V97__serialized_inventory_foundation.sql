CREATE OR REPLACE FUNCTION public.create_serialized_inventory_tables_for_tenant(target_schema TEXT, target_company_id INTEGER)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    product_table REGCLASS;
    balance_table REGCLASS;
    ledger_table REGCLASS;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;

    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    product_table := to_regclass(format('%I.%I', target_schema, 'inventory_product'));
    balance_table := to_regclass(format('%I.%I', target_schema, 'inventory_branch_stock_balance'));
    ledger_table := to_regclass(format('%I.%I', target_schema, 'inventory_stock_ledger'));

    IF product_table IS NULL THEN
        RAISE NOTICE 'Skipping serialized inventory setup for %, inventory_product does not exist', target_schema;
        RETURN;
    END IF;

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            ADD COLUMN IF NOT EXISTS company_id INTEGER,
            ADD COLUMN IF NOT EXISTS tracking_type VARCHAR(20) NOT NULL DEFAULT ''QUANTITY'',
            ADD COLUMN IF NOT EXISTS sku VARCHAR(100),
            ADD COLUMN IF NOT EXISTS barcode VARCHAR(100),
            ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0
    ', target_schema);

    EXECUTE format('
        UPDATE %I.inventory_product
        SET company_id = %s
        WHERE company_id IS NULL OR company_id <> %s
    ', target_schema, target_company_id, target_company_id);

    EXECUTE format('
        UPDATE %I.inventory_product
        SET barcode = serial
        WHERE barcode IS NULL
          AND serial IS NOT NULL
          AND btrim(serial) <> ''''
    ', target_schema);

    EXECUTE format('
        UPDATE %I.inventory_product
        SET tracking_type = upper(btrim(tracking_type))
        WHERE tracking_type IS NOT NULL
    ', target_schema);

    EXECUTE format('
        UPDATE %I.inventory_product
        SET tracking_type = ''QUANTITY''
        WHERE tracking_type IS NULL
           OR btrim(tracking_type) = ''''
           OR tracking_type NOT IN (''QUANTITY'', ''SERIAL'', ''IMEI'', ''BATCH'')
    ', target_schema);

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            ALTER COLUMN company_id SET NOT NULL,
            ALTER COLUMN company_id SET DEFAULT %s,
            ALTER COLUMN tracking_type SET NOT NULL,
            ALTER COLUMN tracking_type SET DEFAULT ''QUANTITY'',
            ALTER COLUMN version SET NOT NULL,
            ALTER COLUMN version SET DEFAULT 0
    ', target_schema, target_company_id);

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = product_table
          AND conname = 'inventory_product_tracking_type_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product
                ADD CONSTRAINT inventory_product_tracking_type_ck
                CHECK (tracking_type IN (''QUANTITY'', ''SERIAL'', ''IMEI'', ''BATCH''))
        ', target_schema);
    END IF;

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_product (company_id, lower(sku))
            WHERE sku IS NOT NULL AND btrim(sku) <> ''''
    ', 'ux_' || target_schema || '_inventory_product_company_sku_ci', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_product (company_id, lower(barcode))
            WHERE barcode IS NOT NULL AND btrim(barcode) <> ''''
    ', 'ux_' || target_schema || '_inventory_product_company_barcode_ci', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_product (company_id, tracking_type, product_id DESC)
    ', 'idx_' || target_schema || '_inventory_product_company_tracking', target_schema);

    IF balance_table IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_branch_stock_balance
                ADD COLUMN IF NOT EXISTS company_id INTEGER,
                ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0
        ', target_schema);

        EXECUTE format('
            UPDATE %I.inventory_branch_stock_balance
            SET company_id = %s
            WHERE company_id IS NULL OR company_id <> %s
        ', target_schema, target_company_id, target_company_id);

        EXECUTE format('
            ALTER TABLE %I.inventory_branch_stock_balance
                ALTER COLUMN company_id SET NOT NULL,
                ALTER COLUMN company_id SET DEFAULT %s,
                ALTER COLUMN version SET NOT NULL,
                ALTER COLUMN version SET DEFAULT 0
        ', target_schema, target_company_id);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.inventory_branch_stock_balance (company_id, branch_id, product_id)
        ', 'idx_' || target_schema || '_inventory_branch_stock_balance_company_branch_product', target_schema);
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.inventory_product_unit (
            product_unit_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            product_id BIGINT NOT NULL,
            tracking_type VARCHAR(20) NOT NULL,
            unit_identifier VARCHAR(100) NOT NULL,
            imei VARCHAR(32),
            serial_number VARCHAR(100),
            status VARCHAR(30) NOT NULL DEFAULT ''AVAILABLE'',
            condition_code VARCHAR(30) NOT NULL DEFAULT ''NEW'',
            supplier_id INTEGER,
            purchase_reference_type VARCHAR(40),
            purchase_reference_id VARCHAR(64),
            purchase_line_id BIGINT,
            sale_order_id BIGINT,
            sale_order_detail_id BIGINT,
            customer_id BIGINT,
            current_transfer_id BIGINT,
            received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            sold_at TIMESTAMP,
            returned_at TIMESTAMP,
            status_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT inventory_product_unit_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT inventory_product_unit_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT inventory_product_unit_product_fk
                FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id) ON DELETE CASCADE,
            CONSTRAINT inventory_product_unit_tracking_type_ck
                CHECK (tracking_type IN (''SERIAL'', ''IMEI'')),
            CONSTRAINT inventory_product_unit_status_ck
                CHECK (status IN (
                    ''AVAILABLE'', ''RESERVED'', ''SOLD'', ''RETURNED'', ''DAMAGED'',
                    ''LOST'', ''TRANSFERRED'', ''UNDER_REPAIR''
                )),
            CONSTRAINT inventory_product_unit_identifier_ck
                CHECK (btrim(unit_identifier) <> ''''),
            CONSTRAINT inventory_product_unit_identifier_type_ck
                CHECK (
                    (tracking_type = ''IMEI'' AND imei IS NOT NULL AND btrim(imei) <> '''')
                    OR
                    (tracking_type = ''SERIAL'' AND serial_number IS NOT NULL AND btrim(serial_number) <> '''')
                )
        )
    ', target_schema, target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (company_id, lower(unit_identifier))
    ', 'ux_' || target_schema || '_inventory_product_unit_company_identifier_ci', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (company_id, lower(imei))
            WHERE imei IS NOT NULL AND btrim(imei) <> ''''
    ', 'ux_' || target_schema || '_inventory_product_unit_company_imei_ci', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (company_id, lower(serial_number))
            WHERE serial_number IS NOT NULL AND btrim(serial_number) <> ''''
    ', 'ux_' || target_schema || '_inventory_product_unit_company_serial_ci', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (company_id, branch_id, status, product_id)
    ', 'idx_' || target_schema || '_inventory_product_unit_company_branch_status_product', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (company_id, product_id, status)
    ', 'idx_' || target_schema || '_inventory_product_unit_company_product_status', target_schema);

    IF ledger_table IS NOT NULL THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_stock_ledger
                ADD COLUMN IF NOT EXISTS company_id INTEGER,
                ADD COLUMN IF NOT EXISTS product_unit_id BIGINT,
                ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160)
        ', target_schema);

        EXECUTE format('
            UPDATE %I.inventory_stock_ledger
            SET company_id = %s
            WHERE company_id IS NULL OR company_id <> %s
        ', target_schema, target_company_id, target_company_id);

        EXECUTE format('
            ALTER TABLE %I.inventory_stock_ledger
                ALTER COLUMN company_id SET NOT NULL,
                ALTER COLUMN company_id SET DEFAULT %s
        ', target_schema, target_company_id);

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = ledger_table
              AND conname = 'inventory_stock_ledger_product_unit_fk'
        ) THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_stock_ledger
                    ADD CONSTRAINT inventory_stock_ledger_product_unit_fk
                    FOREIGN KEY (product_unit_id)
                    REFERENCES %I.inventory_product_unit (product_unit_id)
            ', target_schema, target_schema);
        END IF;

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I.inventory_stock_ledger (company_id, idempotency_key)
                WHERE idempotency_key IS NOT NULL AND btrim(idempotency_key) <> ''''
        ', 'ux_' || target_schema || '_inventory_stock_ledger_company_idempotency', target_schema);
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.inventory_stock_movement (
            stock_movement_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER,
            product_id BIGINT NOT NULL,
            product_unit_id BIGINT,
            movement_type VARCHAR(40) NOT NULL,
            quantity_delta NUMERIC(19,4),
            from_branch_id INTEGER,
            to_branch_id INTEGER,
            reference_type VARCHAR(40),
            reference_id VARCHAR(64),
            reference_line_id BIGINT,
            supplier_id INTEGER,
            customer_id BIGINT,
            actor_user_id BIGINT,
            actor_name VARCHAR(100),
            note VARCHAR(255),
            idempotency_key VARCHAR(160),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT inventory_stock_movement_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT inventory_stock_movement_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT inventory_stock_movement_product_fk
                FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id),
            CONSTRAINT inventory_stock_movement_unit_fk
                FOREIGN KEY (product_unit_id) REFERENCES %I.inventory_product_unit (product_unit_id),
            CONSTRAINT inventory_stock_movement_type_ck
                CHECK (movement_type IN (
                    ''STOCK_IN'', ''SALE'', ''RETURN'', ''TRANSFER_OUT'', ''TRANSFER_IN'',
                    ''DAMAGE'', ''ADJUSTMENT'', ''RESERVE'', ''UNRESERVE'', ''LOST'',
                    ''REPAIR_IN'', ''REPAIR_OUT''
                )),
            CONSTRAINT inventory_stock_movement_quantity_ck
                CHECK (
                    (product_unit_id IS NULL AND quantity_delta IS NOT NULL)
                    OR
                    (product_unit_id IS NOT NULL AND quantity_delta IS NOT NULL AND quantity_delta IN (-1, 0, 1))
                )
        )
    ', target_schema, target_schema, target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_stock_movement (company_id, branch_id, product_id, created_at DESC)
    ', 'idx_' || target_schema || '_inventory_stock_movement_company_branch_product_time', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_stock_movement (company_id, product_unit_id, created_at DESC)
            WHERE product_unit_id IS NOT NULL
    ', 'idx_' || target_schema || '_inventory_stock_movement_company_unit_time', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_stock_movement (company_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL AND btrim(idempotency_key) <> ''''
    ', 'ux_' || target_schema || '_inventory_stock_movement_company_idempotency', target_schema);
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
            PERFORM public.create_serialized_inventory_tables_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
