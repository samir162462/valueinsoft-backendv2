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

        EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_product (
                product_id BIGSERIAL PRIMARY KEY,
                product_name VARCHAR(30) NOT NULL,
                buying_day TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                activation_period INTEGER NOT NULL DEFAULT 0,
                retail_price INTEGER NOT NULL,
                lowest_price INTEGER NOT NULL,
                buying_price INTEGER NOT NULL,
                company_name VARCHAR(30) NOT NULL,
                product_type VARCHAR(15) NOT NULL,
                owner_name VARCHAR(20),
                serial VARCHAR(35),
                description VARCHAR(60),
                battery_life INTEGER NOT NULL DEFAULT 0,
                owner_phone VARCHAR(14),
                owner_ni VARCHAR(18),
                product_state VARCHAR(10) NOT NULL,
                supplier_id INTEGER NOT NULL DEFAULT 0,
                major VARCHAR(30) NOT NULL,
                img_file TEXT,
                business_line_key VARCHAR(40) NOT NULL DEFAULT ''MOBILE'',
                template_key VARCHAR(80) NOT NULL DEFAULT ''mobile_device'',
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT inventory_product_price_order_ck CHECK (retail_price >= lowest_price AND lowest_price >= buying_price),
                CONSTRAINT inventory_product_state_ck CHECK (product_state IN (''New'', ''Used'')),
                CONSTRAINT inventory_product_activation_ck CHECK (activation_period >= 0),
                CONSTRAINT inventory_product_battery_ck CHECK (battery_life >= 0)
            )', schema_name);

        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product (major, product_id DESC)',
            'idx_' || schema_name || '_inventory_product_major', schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product (product_name)',
            'idx_' || schema_name || '_inventory_product_name', schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product (serial)',
            'idx_' || schema_name || '_inventory_product_serial', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_branch_stock_balance (
                branch_id INTEGER NOT NULL,
                product_id BIGINT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 0,
                reserved_qty INTEGER NOT NULL DEFAULT 0,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (branch_id, product_id),
                CONSTRAINT inventory_branch_stock_balance_branch_fk FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId") ON DELETE CASCADE,
                CONSTRAINT inventory_branch_stock_balance_product_fk FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id) ON DELETE CASCADE,
                CONSTRAINT inventory_branch_stock_balance_quantity_ck CHECK (quantity >= 0),
                CONSTRAINT inventory_branch_stock_balance_reserved_ck CHECK (reserved_qty >= 0)
            )', schema_name, schema_name);

        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_branch_stock_balance (branch_id, quantity DESC)',
            'idx_' || schema_name || '_inventory_branch_stock_balance_branch', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_stock_ledger (
                stock_ledger_id BIGSERIAL PRIMARY KEY,
                branch_id INTEGER NOT NULL,
                product_id BIGINT NOT NULL,
                quantity_delta INTEGER NOT NULL,
                movement_type VARCHAR(40) NOT NULL,
                reference_type VARCHAR(40),
                reference_id VARCHAR(64),
                actor_name VARCHAR(100),
                note VARCHAR(255),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT inventory_stock_ledger_branch_fk FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId") ON DELETE CASCADE,
                CONSTRAINT inventory_stock_ledger_product_fk FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id) ON DELETE CASCADE
            )', schema_name, schema_name);

        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_stock_ledger (branch_id, product_id, created_at DESC)',
            'idx_' || schema_name || '_inventory_stock_ledger_branch_product_time', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_legacy_product_mapping (
                branch_id INTEGER NOT NULL,
                legacy_product_id INTEGER NOT NULL,
                product_id BIGINT NOT NULL,
                synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (branch_id, legacy_product_id),
                CONSTRAINT inventory_legacy_product_mapping_product_fk FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id) ON DELETE CASCADE
            )', schema_name, schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_product_template (
                template_id BIGSERIAL PRIMARY KEY,
                business_line_key VARCHAR(40) NOT NULL,
                template_key VARCHAR(80) NOT NULL UNIQUE,
                display_name VARCHAR(100) NOT NULL,
                major_key VARCHAR(40),
                supports_serial BOOLEAN NOT NULL DEFAULT FALSE,
                supports_batch BOOLEAN NOT NULL DEFAULT FALSE,
                supports_expiry BOOLEAN NOT NULL DEFAULT FALSE,
                supports_weight BOOLEAN NOT NULL DEFAULT FALSE,
                is_system BOOLEAN NOT NULL DEFAULT TRUE,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_attribute_definition (
                attribute_id BIGSERIAL PRIMARY KEY,
                business_line_key VARCHAR(40) NOT NULL,
                attribute_key VARCHAR(80) NOT NULL,
                display_name VARCHAR(100) NOT NULL,
                data_type VARCHAR(20) NOT NULL,
                is_required BOOLEAN NOT NULL DEFAULT FALSE,
                is_filterable BOOLEAN NOT NULL DEFAULT FALSE,
                is_searchable BOOLEAN NOT NULL DEFAULT FALSE,
                field_schema JSONB NOT NULL DEFAULT ''{}''::jsonb,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT inventory_attribute_definition_data_type_ck CHECK (data_type IN (''TEXT'', ''NUMBER'', ''BOOLEAN'', ''DATE'', ''JSON'')),
                CONSTRAINT inventory_attribute_definition_unique_key UNIQUE (business_line_key, attribute_key)
            )', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_template_attribute (
                template_id BIGINT NOT NULL,
                attribute_id BIGINT NOT NULL,
                display_order INTEGER NOT NULL DEFAULT 0,
                is_required BOOLEAN NOT NULL DEFAULT FALSE,
                group_key VARCHAR(80),
                default_value_jsonb JSONB,
                PRIMARY KEY (template_id, attribute_id),
                CONSTRAINT inventory_template_attribute_template_fk FOREIGN KEY (template_id) REFERENCES %I.inventory_product_template (template_id) ON DELETE CASCADE,
                CONSTRAINT inventory_template_attribute_attribute_fk FOREIGN KEY (attribute_id) REFERENCES %I.inventory_attribute_definition (attribute_id) ON DELETE CASCADE
            )', schema_name, schema_name, schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_product_attribute_value (
                product_id BIGINT NOT NULL,
                attribute_id BIGINT NOT NULL,
                value_text TEXT,
                value_number DOUBLE PRECISION,
                value_boolean BOOLEAN,
                value_date DATE,
                value_jsonb JSONB,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (product_id, attribute_id),
                CONSTRAINT inventory_product_attribute_value_product_fk FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id) ON DELETE CASCADE,
                CONSTRAINT inventory_product_attribute_value_attribute_fk FOREIGN KEY (attribute_id) REFERENCES %I.inventory_attribute_definition (attribute_id) ON DELETE CASCADE
            )', schema_name, schema_name, schema_name);

        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product_attribute_value (attribute_id, value_text)',
            'idx_' || schema_name || '_inventory_product_attribute_value_text', schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product_attribute_value (attribute_id, value_number)',
            'idx_' || schema_name || '_inventory_product_attribute_value_number', schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product_attribute_value (attribute_id, value_boolean)',
            'idx_' || schema_name || '_inventory_product_attribute_value_boolean', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_product_template
                (business_line_key, template_key, display_name, major_key, supports_serial, supports_batch, supports_expiry, supports_weight, is_system, is_active, created_at, updated_at)
            SELECT business_line_key, template_key, display_name, major_key, supports_serial, supports_batch, supports_expiry, supports_weight, is_system, is_active, created_at, updated_at
            FROM public.inventory_product_template
            ON CONFLICT (template_key) DO NOTHING', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_attribute_definition
                (business_line_key, attribute_key, display_name, data_type, is_required, is_filterable, is_searchable, field_schema, created_at, updated_at)
            SELECT business_line_key, attribute_key, display_name, data_type, is_required, is_filterable, is_searchable, field_schema, created_at, updated_at
            FROM public.inventory_attribute_definition
            ON CONFLICT (business_line_key, attribute_key) DO NOTHING', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_template_attribute
                (template_id, attribute_id, display_order, is_required, group_key, default_value_jsonb)
            SELECT target_template.template_id, target_attribute.attribute_id, source.display_order, source.is_required, source.group_key, source.default_value_jsonb
            FROM public.inventory_template_attribute source
            JOIN public.inventory_product_template source_template ON source_template.template_id = source.template_id
            JOIN public.inventory_attribute_definition source_attribute ON source_attribute.attribute_id = source.attribute_id
            JOIN %I.inventory_product_template target_template ON target_template.template_key = source_template.template_key
            JOIN %I.inventory_attribute_definition target_attribute
              ON target_attribute.business_line_key = source_attribute.business_line_key
             AND target_attribute.attribute_key = source_attribute.attribute_key
            ON CONFLICT (template_id, attribute_id) DO NOTHING', schema_name, schema_name, schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_product
                (product_id, product_name, buying_day, activation_period, retail_price, lowest_price, buying_price,
                 company_name, product_type, owner_name, serial, description, battery_life, owner_phone,
                 owner_ni, product_state, supplier_id, major, img_file, business_line_key, template_key, created_at, updated_at)
            SELECT product_id, product_name, buying_day, activation_period, retail_price, lowest_price, buying_price,
                   company_name, product_type, owner_name, serial, description, battery_life, owner_phone,
                   owner_ni, product_state, supplier_id, major, img_file, business_line_key, template_key, created_at, updated_at
            FROM public.inventory_product
            WHERE company_id = %s
            ON CONFLICT (product_id) DO NOTHING', schema_name, company_record.id);

        EXECUTE format('SELECT setval(pg_get_serial_sequence(''%I.inventory_product'', ''product_id''), COALESCE((SELECT MAX(product_id) FROM %I.inventory_product), 1), true)',
            schema_name, schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_branch_stock_balance
                (branch_id, product_id, quantity, reserved_qty, updated_at)
            SELECT branch_id, product_id, quantity, reserved_qty, updated_at
            FROM public.inventory_branch_stock_balance
            WHERE company_id = %s
            ON CONFLICT (branch_id, product_id) DO NOTHING', schema_name, company_record.id);

        EXECUTE format('
            INSERT INTO %I.inventory_stock_ledger
                (stock_ledger_id, branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id, actor_name, note, created_at)
            SELECT stock_ledger_id, branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id, actor_name, note, created_at
            FROM public.inventory_stock_ledger
            WHERE company_id = %s
            ON CONFLICT (stock_ledger_id) DO NOTHING', schema_name, company_record.id);

        EXECUTE format('SELECT setval(pg_get_serial_sequence(''%I.inventory_stock_ledger'', ''stock_ledger_id''), COALESCE((SELECT MAX(stock_ledger_id) FROM %I.inventory_stock_ledger), 1), true)',
            schema_name, schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_legacy_product_mapping
                (branch_id, legacy_product_id, product_id, synced_at)
            SELECT branch_id, legacy_product_id, product_id, synced_at
            FROM public.inventory_legacy_product_mapping
            WHERE company_id = %s
            ON CONFLICT (branch_id, legacy_product_id) DO NOTHING', schema_name, company_record.id);

        EXECUTE format('
            INSERT INTO %I.inventory_product_attribute_value
                (product_id, attribute_id, value_text, value_number, value_boolean, value_date, value_jsonb, updated_at)
            SELECT product_target.product_id,
                   attribute_target.attribute_id,
                   value_source.value_text,
                   value_source.value_number,
                   value_source.value_boolean,
                   value_source.value_date,
                   value_source.value_jsonb,
                   value_source.updated_at
            FROM public.inventory_product_attribute_value value_source
            JOIN public.inventory_product product_source ON product_source.product_id = value_source.product_id AND product_source.company_id = %s
            JOIN %I.inventory_product product_target ON product_target.product_id = product_source.product_id
            JOIN public.inventory_attribute_definition attribute_source ON attribute_source.attribute_id = value_source.attribute_id
            JOIN %I.inventory_attribute_definition attribute_target
              ON attribute_target.business_line_key = attribute_source.business_line_key
             AND attribute_target.attribute_key = attribute_source.attribute_key
            ON CONFLICT (product_id, attribute_id) DO NOTHING', schema_name, company_record.id, schema_name, schema_name);
    END LOOP;
END $$;
