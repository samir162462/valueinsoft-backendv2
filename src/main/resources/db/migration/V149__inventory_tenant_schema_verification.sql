CREATE TABLE IF NOT EXISTS public.inventory_tenant_schema_version (
    company_id INTEGER PRIMARY KEY,
    schema_name VARCHAR(80) NOT NULL UNIQUE,
    inventory_foundation_version INTEGER NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT inventory_tenant_schema_version_company_fk
        FOREIGN KEY (company_id) REFERENCES public."Company" (id) ON DELETE CASCADE,
    CONSTRAINT inventory_tenant_schema_version_company_ck CHECK (company_id > 0),
    CONSTRAINT inventory_tenant_schema_version_version_ck CHECK (inventory_foundation_version > 0)
);

CREATE OR REPLACE FUNCTION public.ensure_inventory_workspace_receipt_foundation_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    expected_schema TEXT := format('c_%s', target_company_id);
BEGIN
    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;
    IF target_schema IS NULL OR target_schema <> expected_schema THEN
        RAISE EXCEPTION 'target_schema must equal %', expected_schema;
    END IF;
    IF to_regnamespace(target_schema) IS NULL THEN
        RAISE EXCEPTION 'Tenant schema % does not exist', target_schema;
    END IF;
    IF to_regclass(format('%I.inventory_product', target_schema)) IS NULL THEN
        RAISE EXCEPTION 'Tenant schema % is missing inventory_product', target_schema;
    END IF;

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.inventory_operation_idempotency (
            id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL,
            branch_id INTEGER NOT NULL,
            operation_type VARCHAR(80) NOT NULL,
            idempotency_key VARCHAR(160) NOT NULL,
            request_hash VARCHAR(128) NOT NULL,
            status VARCHAR(30) NOT NULL,
            response_payload JSONB,
            created_by VARCHAR(120),
            operation_id VARCHAR(80) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            completed_at TIMESTAMP,
            CONSTRAINT inventory_operation_idempotency_status_ck
                CHECK (status IN (''PENDING'', ''COMPLETED'')),
            CONSTRAINT inventory_operation_idempotency_company_fk
                FOREIGN KEY (company_id) REFERENCES public."Company" (id),
            CONSTRAINT inventory_operation_idempotency_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId")
        )
    ', target_schema);

    EXECUTE format('
        CREATE UNIQUE INDEX IF NOT EXISTS %I
            ON %I.inventory_operation_idempotency
                (company_id, branch_id, operation_type, idempotency_key)
    ', 'ux_' || target_schema || '_inventory_operation_idempotency_key', target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_operation_idempotency
                (company_id, branch_id, operation_type, status, created_at DESC)
    ', 'idx_' || target_schema || '_inventory_operation_idempotency_status', target_schema);

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.inventory_branch_product (
            branch_id INTEGER NOT NULL,
            product_id BIGINT NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            reorder_level NUMERIC(19, 4),
            default_supplier_id INTEGER,
            assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            assigned_by VARCHAR(100),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (branch_id, product_id),
            CONSTRAINT inventory_branch_product_reorder_level_chk
                CHECK (reorder_level IS NULL OR reorder_level >= 0),
            CONSTRAINT inventory_branch_product_branch_fk
                FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId"),
            CONSTRAINT inventory_branch_product_product_fk
                FOREIGN KEY (product_id) REFERENCES %I.inventory_product (product_id)
        )
    ', target_schema, target_schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
            ON %I.inventory_branch_product (product_id, branch_id)
    ', 'idx_' || target_schema || '_inventory_branch_product_product_branch', target_schema);

    IF to_regclass(format('%I.inventory_branch_stock_balance', target_schema)) IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO %I.inventory_branch_product
                (branch_id, product_id, is_active, assigned_at, created_at, updated_at)
            SELECT branch_id, product_id, TRUE, updated_at, updated_at, updated_at
            FROM %I.inventory_branch_stock_balance
            ON CONFLICT (branch_id, product_id) DO NOTHING
        ', target_schema, target_schema);
    END IF;

    IF to_regclass(format('%I.inventory_legacy_product_mapping', target_schema)) IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO %I.inventory_branch_product
                (branch_id, product_id, is_active, assigned_at, created_at, updated_at)
            SELECT branch_id, product_id, TRUE, synced_at, synced_at, synced_at
            FROM %I.inventory_legacy_product_mapping
            ON CONFLICT (branch_id, product_id) DO NOTHING
        ', target_schema, target_schema);
    END IF;

    IF to_regclass(format('%I.inventory_stock_ledger', target_schema)) IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO %I.inventory_branch_product
                (branch_id, product_id, is_active, assigned_at, created_at, updated_at)
            SELECT branch_id, product_id, TRUE, MIN(created_at), MIN(created_at), MAX(created_at)
            FROM %I.inventory_stock_ledger
            GROUP BY branch_id, product_id
            ON CONFLICT (branch_id, product_id) DO NOTHING
        ', target_schema, target_schema);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.inventory_stock_ledger
                    (company_id, branch_id, product_id, reference_type, reference_id)
        ', 'idx_' || target_schema || '_inventory_stock_ledger_receipt_ref', target_schema);
    END IF;

    IF to_regclass(format('%I.inventory_product_unit', target_schema)) IS NOT NULL THEN
        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.inventory_product_unit
                    (company_id, product_id, purchase_reference_type, purchase_reference_id)
        ', 'idx_' || target_schema || '_inventory_product_unit_purchase_ref', target_schema);
    END IF;

    INSERT INTO public.inventory_tenant_schema_version (
        company_id,
        schema_name,
        inventory_foundation_version,
        verified_at
    ) VALUES (
        target_company_id,
        target_schema,
        149,
        NOW()
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
           AND to_regclass(format('%I.inventory_product', target_schema)) IS NOT NULL THEN
            PERFORM public.ensure_inventory_workspace_receipt_foundation_for_tenant(
                target_schema,
                company_record.id
            );
        END IF;
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION public.inventory_tenant_schema_drift()
RETURNS TABLE (
    company_id INTEGER,
    schema_name TEXT,
    missing_object TEXT
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        company.id,
        format('c_%s', company.id),
        required.object_name
    FROM public."Company" company
    CROSS JOIN (
        VALUES
            ('inventory_product'),
            ('inventory_branch_stock_balance'),
            ('inventory_stock_ledger'),
            ('inventory_operation_idempotency'),
            ('inventory_branch_product'),
            ('inventory_product_unit'),
            ('inventory_stock_movement')
    ) AS required(object_name)
    WHERE to_regclass(format('%I.%I', format('c_%s', company.id), required.object_name)) IS NULL
    ORDER BY company.id, required.object_name;
$$;
