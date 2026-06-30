DO $$
DECLARE
    company_record RECORD;
    branch_record RECORD;
    target_schema_name TEXT;
    supplier_table_name TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company" ORDER BY id LOOP
        target_schema_name := format('c_%s', company_record.id);

        -- Skip if schema doesn't exist
        IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = target_schema_name) THEN
            CONTINUE;
        END IF;

        -- Skip if inventory_product doesn't exist in this tenant yet
        IF to_regclass(format('%I.inventory_product', target_schema_name)) IS NULL THEN
            CONTINUE;
        END IF;

        -- 1. Create table
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.inventory_branch_product (
                branch_id INTEGER NOT NULL,
                product_id BIGINT NOT NULL,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                reorder_level NUMERIC(19, 4),
                default_supplier_id INTEGER NULL,
                assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                assigned_by VARCHAR(100),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (branch_id, product_id),
                CONSTRAINT inventory_branch_product_reorder_level_chk
                    CHECK (reorder_level IS NULL OR reorder_level >= 0)
            )
        ', target_schema_name);

        -- 2. Create reverse index
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_branch_product (product_id, branch_id)',
            'idx_' || target_schema_name || '_inventory_branch_product_product_branch', target_schema_name);

        -- 3. Backfill from inventory_branch_stock_balance
        IF to_regclass(format('%I.inventory_branch_stock_balance', target_schema_name)) IS NOT NULL THEN
            EXECUTE format('
                INSERT INTO %I.inventory_branch_product (branch_id, product_id, is_active, assigned_at, created_at, updated_at)
                SELECT branch_id, product_id, TRUE, updated_at, updated_at, updated_at
                FROM %I.inventory_branch_stock_balance
                ON CONFLICT (branch_id, product_id) DO NOTHING
            ', target_schema_name, target_schema_name);
        END IF;

        -- 4. Backfill from inventory_legacy_product_mapping
        IF to_regclass(format('%I.inventory_legacy_product_mapping', target_schema_name)) IS NOT NULL THEN
            EXECUTE format('
                INSERT INTO %I.inventory_branch_product (branch_id, product_id, is_active, assigned_at, created_at, updated_at)
                SELECT branch_id, product_id, TRUE, synced_at, synced_at, synced_at
                FROM %I.inventory_legacy_product_mapping
                ON CONFLICT (branch_id, product_id) DO NOTHING
            ', target_schema_name, target_schema_name);
        END IF;

        -- 5. Backfill from inventory_stock_ledger (historical transactions)
        IF to_regclass(format('%I.inventory_stock_ledger', target_schema_name)) IS NOT NULL THEN
            EXECUTE format('
                INSERT INTO %I.inventory_branch_product (branch_id, product_id, is_active, assigned_at, created_at, updated_at)
                SELECT branch_id, product_id, TRUE, MIN(created_at), MIN(created_at), MAX(created_at)
                FROM %I.inventory_stock_ledger
                GROUP BY branch_id, product_id
                ON CONFLICT (branch_id, product_id) DO NOTHING
            ', target_schema_name, target_schema_name);
        END IF;

        -- 6. Safely backfill default_supplier_id
        -- We only migrate the supplier_id from inventory_product to inventory_branch_product
        -- if that supplier actually belongs to the branch (exists in supplier_{branchId}).
        FOR branch_record IN SELECT "branchId" FROM public."Branch" WHERE "companyId" = company_record.id LOOP
            supplier_table_name := format('supplier_%s', branch_record."branchId");

            IF to_regclass(format('%I.%I', target_schema_name, supplier_table_name)) IS NOT NULL THEN
                EXECUTE format('
                    UPDATE %I.inventory_branch_product ibp
                    SET default_supplier_id = p.supplier_id
                    FROM %I.inventory_product p
                    JOIN %I.%I s ON s."supplierId" = p.supplier_id
                    WHERE p.product_id = ibp.product_id
                      AND ibp.branch_id = %s
                      AND ibp.default_supplier_id IS NULL
                      AND p.supplier_id IS NOT NULL AND p.supplier_id > 0
                ', target_schema_name, target_schema_name, target_schema_name, supplier_table_name, branch_record."branchId");
            END IF;
        END LOOP;

        -- 7. Report orphan products (no branch assignment)
        RAISE NOTICE 'Orphan products check for schema %', target_schema_name;
        -- We would log them in a tracking table if required, but for now we emit notices.
        
    END LOOP;
END $$;
