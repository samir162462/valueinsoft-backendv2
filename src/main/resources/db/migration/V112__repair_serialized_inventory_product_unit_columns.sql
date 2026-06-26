CREATE OR REPLACE FUNCTION public.ensure_serialized_inventory_product_unit_table_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    product_table REGCLASS;
    unit_table REGCLASS;
    seq_name TEXT := 'inventory_product_unit_product_unit_id_seq';
    seq_regclass TEXT;
    null_count BIGINT;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;

    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    product_table := to_regclass(format('%I.%I', target_schema, 'inventory_product'));
    IF product_table IS NULL THEN
        RAISE NOTICE 'Skipping serialized inventory product unit repair for %, inventory_product does not exist', target_schema;
        RETURN;
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
            version BIGINT NOT NULL DEFAULT 0
        )
    ', target_schema);

    unit_table := to_regclass(format('%I.%I', target_schema, 'inventory_product_unit'));
    IF unit_table IS NULL THEN
        RAISE EXCEPTION 'inventory_product_unit was not created for %', target_schema;
    END IF;

    EXECUTE format('CREATE SEQUENCE IF NOT EXISTS %I.%I AS BIGINT', target_schema, seq_name);
    seq_regclass := format('%I.%I', target_schema, seq_name);

    EXECUTE format('ALTER TABLE %I.inventory_product_unit ADD COLUMN IF NOT EXISTS product_unit_id BIGINT', target_schema);
    EXECUTE format(
        'ALTER TABLE %I.inventory_product_unit ALTER COLUMN product_unit_id SET DEFAULT nextval(%L::regclass)',
        target_schema,
        seq_regclass
    );
    EXECUTE format(
        'UPDATE %I.inventory_product_unit SET product_unit_id = nextval(%L::regclass) WHERE product_unit_id IS NULL',
        target_schema,
        seq_regclass
    );
    EXECUTE format(
        'SELECT setval(%L::regclass, COALESCE((SELECT MAX(product_unit_id) FROM %I.inventory_product_unit), 0) + 1, false)',
        seq_regclass,
        target_schema
    );
    EXECUTE format('ALTER SEQUENCE %I.%I OWNED BY %I.inventory_product_unit.product_unit_id', target_schema, seq_name, target_schema);

    EXECUTE format('
        ALTER TABLE %I.inventory_product_unit
            ADD COLUMN IF NOT EXISTS company_id INTEGER,
            ADD COLUMN IF NOT EXISTS branch_id INTEGER,
            ADD COLUMN IF NOT EXISTS product_id BIGINT,
            ADD COLUMN IF NOT EXISTS tracking_type VARCHAR(20),
            ADD COLUMN IF NOT EXISTS unit_identifier VARCHAR(100),
            ADD COLUMN IF NOT EXISTS imei VARCHAR(32),
            ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100),
            ADD COLUMN IF NOT EXISTS status VARCHAR(30),
            ADD COLUMN IF NOT EXISTS condition_code VARCHAR(30),
            ADD COLUMN IF NOT EXISTS supplier_id INTEGER,
            ADD COLUMN IF NOT EXISTS purchase_reference_type VARCHAR(40),
            ADD COLUMN IF NOT EXISTS purchase_reference_id VARCHAR(64),
            ADD COLUMN IF NOT EXISTS purchase_line_id BIGINT,
            ADD COLUMN IF NOT EXISTS sale_order_id BIGINT,
            ADD COLUMN IF NOT EXISTS sale_order_detail_id BIGINT,
            ADD COLUMN IF NOT EXISTS customer_id BIGINT,
            ADD COLUMN IF NOT EXISTS current_transfer_id BIGINT,
            ADD COLUMN IF NOT EXISTS received_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS sold_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS returned_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS version BIGINT
    ', target_schema);

    EXECUTE format('
        UPDATE %I.inventory_product_unit
        SET company_id = %s
        WHERE company_id IS NULL OR company_id <> %s
    ', target_schema, target_company_id, target_company_id);

    EXECUTE format('
        UPDATE %I.inventory_product_unit
        SET tracking_type = CASE
                WHEN upper(btrim(COALESCE(tracking_type, ''''))) IN (''SERIAL'', ''IMEI'')
                    THEN upper(btrim(tracking_type))
                WHEN imei IS NOT NULL AND btrim(imei) <> ''''
                    THEN ''IMEI''
                ELSE ''SERIAL''
            END,
            unit_identifier = COALESCE(
                NULLIF(btrim(unit_identifier), ''''),
                NULLIF(btrim(imei), ''''),
                NULLIF(btrim(serial_number), ''''),
                product_unit_id::text
            ),
            status = CASE
                WHEN upper(btrim(COALESCE(status, ''''))) IN (
                    ''AVAILABLE'', ''RESERVED'', ''SOLD'', ''RETURNED'', ''DAMAGED'',
                    ''LOST'', ''TRANSFERRED'', ''UNDER_REPAIR''
                )
                    THEN upper(btrim(status))
                ELSE ''AVAILABLE''
            END,
            condition_code = COALESCE(NULLIF(btrim(condition_code), ''''), ''NEW''),
            received_at = COALESCE(received_at, CURRENT_TIMESTAMP),
            status_updated_at = COALESCE(status_updated_at, CURRENT_TIMESTAMP),
            created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
            updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP),
            version = COALESCE(version, 0)
    ', target_schema);

    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN company_id SET DEFAULT %s', target_schema, target_company_id);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN status SET DEFAULT ''AVAILABLE''', target_schema);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN condition_code SET DEFAULT ''NEW''', target_schema);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN received_at SET DEFAULT CURRENT_TIMESTAMP', target_schema);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN status_updated_at SET DEFAULT CURRENT_TIMESTAMP', target_schema);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP', target_schema);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP', target_schema);
    EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN version SET DEFAULT 0', target_schema);

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE product_unit_id IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN product_unit_id SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE company_id IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN company_id SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE tracking_type IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN tracking_type SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE unit_identifier IS NULL OR btrim(unit_identifier) = ''''', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN unit_identifier SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE status IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN status SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE condition_code IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN condition_code SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE received_at IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN received_at SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE status_updated_at IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN status_updated_at SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE created_at IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN created_at SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE updated_at IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN updated_at SET NOT NULL', target_schema);
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_product_unit WHERE version IS NULL', target_schema) INTO null_count;
    IF null_count = 0 THEN
        EXECUTE format('ALTER TABLE %I.inventory_product_unit ALTER COLUMN version SET NOT NULL', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND contype = 'p'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_pkey PRIMARY KEY (product_unit_id)
        ', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = 'inventory_product_unit_company_fk'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id)
                NOT VALID
        ', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = 'inventory_product_unit_branch_fk'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId")
                NOT VALID
        ', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = 'inventory_product_unit_product_fk'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_product_fk
                FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id) ON DELETE CASCADE
                NOT VALID
        ', target_schema, target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = 'inventory_product_unit_tracking_type_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_tracking_type_ck
                CHECK (tracking_type IN (''SERIAL'', ''IMEI''))
                NOT VALID
        ', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = 'inventory_product_unit_status_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_status_ck
                CHECK (status IN (
                    ''AVAILABLE'', ''RESERVED'', ''SOLD'', ''RETURNED'', ''DAMAGED'',
                    ''LOST'', ''TRANSFERRED'', ''UNDER_REPAIR''
                ))
                NOT VALID
        ', target_schema);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = unit_table
          AND conname = 'inventory_product_unit_identifier_ck'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_identifier_ck
                CHECK (unit_identifier IS NOT NULL AND btrim(unit_identifier) <> '''')
                NOT VALID
        ', target_schema);
    END IF;

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (company_id, lower(unit_identifier))
            WHERE unit_identifier IS NOT NULL AND btrim(unit_identifier) <> ''''
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

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_product_unit (branch_id, sale_order_detail_id)
            WHERE sale_order_detail_id IS NOT NULL
    ', 'idx_' || target_schema || '_inventory_product_unit_branch_sale_order_detail', target_schema);
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
            PERFORM public.ensure_serialized_inventory_product_unit_table_for_tenant(schema_name, company_record.id);
        END IF;
    END LOOP;
END;
$$;
