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

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_uom_dimension (
                dimension_key VARCHAR(20) PRIMARY KEY,
                display_name VARCHAR(60) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )', schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_uom_unit (
                uom_code VARCHAR(20) PRIMARY KEY,
                display_name VARCHAR(60) NOT NULL,
                dimension_key VARCHAR(20) NOT NULL,
                precision_scale INTEGER NOT NULL DEFAULT 0,
                is_base BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT inventory_uom_unit_dimension_fk
                    FOREIGN KEY (dimension_key) REFERENCES %I.inventory_uom_dimension (dimension_key) ON DELETE CASCADE
            )', schema_name, schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_uom_conversion (
                from_uom_code VARCHAR(20) NOT NULL,
                to_uom_code VARCHAR(20) NOT NULL,
                multiplier NUMERIC(18,6) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (from_uom_code, to_uom_code),
                CONSTRAINT inventory_uom_conversion_from_fk
                    FOREIGN KEY (from_uom_code) REFERENCES %I.inventory_uom_unit (uom_code) ON DELETE CASCADE,
                CONSTRAINT inventory_uom_conversion_to_fk
                    FOREIGN KEY (to_uom_code) REFERENCES %I.inventory_uom_unit (uom_code) ON DELETE CASCADE,
                CONSTRAINT inventory_uom_conversion_multiplier_ck CHECK (multiplier > 0)
            )', schema_name, schema_name, schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_pricing_policy (
                pricing_policy_code VARCHAR(40) PRIMARY KEY,
                display_name VARCHAR(100) NOT NULL,
                strategy_type VARCHAR(40) NOT NULL,
                config_json JSONB NOT NULL DEFAULT ''{}''::jsonb,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )', schema_name);

        EXECUTE format('
            ALTER TABLE %I.inventory_product
                ADD COLUMN IF NOT EXISTS base_uom_code VARCHAR(20) NOT NULL DEFAULT ''PCS'',
                ADD COLUMN IF NOT EXISTS pricing_policy_code VARCHAR(40) NOT NULL DEFAULT ''FIXED_RETAIL''', schema_name);

        EXECUTE format('
            ALTER TABLE %I.inventory_product
                DROP CONSTRAINT IF EXISTS inventory_product_uom_fk', schema_name);
        EXECUTE format('
            ALTER TABLE %I.inventory_product
                DROP CONSTRAINT IF EXISTS inventory_product_pricing_policy_fk', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_uom_dimension (dimension_key, display_name)
            VALUES
                (''COUNT'', ''Count''),
                (''WEIGHT'', ''Weight''),
                (''VOLUME'', ''Volume'')
            ON CONFLICT (dimension_key) DO NOTHING', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_uom_unit (uom_code, display_name, dimension_key, precision_scale, is_base)
            VALUES
                (''PCS'', ''Piece'', ''COUNT'', 0, TRUE),
                (''BOX'', ''Box'', ''COUNT'', 0, FALSE),
                (''PACK'', ''Pack'', ''COUNT'', 0, FALSE),
                (''GRAM'', ''Gram'', ''WEIGHT'', 3, TRUE),
                (''KILOGRAM'', ''Kilogram'', ''WEIGHT'', 3, FALSE),
                (''ML'', ''Milliliter'', ''VOLUME'', 2, TRUE),
                (''LITER'', ''Liter'', ''VOLUME'', 2, FALSE)
            ON CONFLICT (uom_code) DO UPDATE
            SET display_name = EXCLUDED.display_name,
                dimension_key = EXCLUDED.dimension_key,
                precision_scale = EXCLUDED.precision_scale,
                is_base = EXCLUDED.is_base,
                updated_at = CURRENT_TIMESTAMP', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_uom_conversion (from_uom_code, to_uom_code, multiplier)
            VALUES
                (''KILOGRAM'', ''GRAM'', 1000.000000),
                (''LITER'', ''ML'', 1000.000000)
            ON CONFLICT (from_uom_code, to_uom_code) DO UPDATE
            SET multiplier = EXCLUDED.multiplier', schema_name);

        EXECUTE format('
            INSERT INTO %I.inventory_pricing_policy (pricing_policy_code, display_name, strategy_type, config_json)
            VALUES
                (''FIXED_RETAIL'', ''Fixed Retail Price'', ''FIXED'', ''{}''::jsonb),
                (''MARKUP_COST'', ''Markup From Cost'', ''MARKUP'', ''{"base":"buying_price"}''::jsonb),
                (''WEIGHT_MARKET'', ''Weight X Market Rate'', ''WEIGHT_X_MARKET_RATE'', ''{"market":"gold"}''::jsonb),
                (''FORMULA'', ''Formula Based'', ''FORMULA'', ''{}''::jsonb),
                (''BATCH_BASED'', ''Batch Based'', ''BATCH_BASED'', ''{}''::jsonb)
            ON CONFLICT (pricing_policy_code) DO UPDATE
            SET display_name = EXCLUDED.display_name,
                strategy_type = EXCLUDED.strategy_type,
                config_json = EXCLUDED.config_json,
                is_active = TRUE,
                updated_at = CURRENT_TIMESTAMP', schema_name);

        EXECUTE format('
            UPDATE %I.inventory_product
            SET base_uom_code = CASE
                    WHEN business_line_key = ''GOLD'' THEN ''GRAM''
                    WHEN business_line_key = ''CHEMICAL'' THEN ''LITER''
                    ELSE ''PCS''
                END
            WHERE base_uom_code IS NULL OR base_uom_code = ''''', schema_name);

        EXECUTE format('
            UPDATE %I.inventory_product
            SET pricing_policy_code = CASE
                    WHEN business_line_key = ''GOLD'' THEN ''WEIGHT_MARKET''
                    WHEN business_line_key = ''CHEMICAL'' THEN ''FORMULA''
                    ELSE ''FIXED_RETAIL''
                END
            WHERE pricing_policy_code IS NULL OR pricing_policy_code = ''''', schema_name);

        EXECUTE format('
            ALTER TABLE %I.inventory_product
                ADD CONSTRAINT inventory_product_uom_fk
                FOREIGN KEY (base_uom_code) REFERENCES %I.inventory_uom_unit (uom_code)', schema_name, schema_name);

        EXECUTE format('
            ALTER TABLE %I.inventory_product
                ADD CONSTRAINT inventory_product_pricing_policy_fk
                FOREIGN KEY (pricing_policy_code) REFERENCES %I.inventory_pricing_policy (pricing_policy_code)', schema_name, schema_name);
    END LOOP;
END $$;
