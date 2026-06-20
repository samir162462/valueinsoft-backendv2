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

        IF to_regnamespace(schema_name) IS NULL THEN
            CONTINUE;
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
        ', schema_name);

        EXECUTE format('
            CREATE UNIQUE INDEX IF NOT EXISTS %I
                ON %I.inventory_operation_idempotency (company_id, branch_id, operation_type, idempotency_key)
        ', 'ux_' || schema_name || '_inventory_operation_idempotency_key', schema_name);

        EXECUTE format('
            CREATE INDEX IF NOT EXISTS %I
                ON %I.inventory_operation_idempotency (company_id, branch_id, operation_type, status, created_at DESC)
        ', 'idx_' || schema_name || '_inventory_operation_idempotency_status', schema_name);

        IF to_regclass(format('%I.%I', schema_name, 'inventory_stock_ledger')) IS NOT NULL THEN
            EXECUTE format('
                CREATE INDEX IF NOT EXISTS %I
                    ON %I.inventory_stock_ledger (company_id, branch_id, product_id, reference_type, reference_id)
            ', 'idx_' || schema_name || '_inventory_stock_ledger_receipt_ref', schema_name);
        END IF;

        IF to_regclass(format('%I.%I', schema_name, 'inventory_product_unit')) IS NOT NULL THEN
            EXECUTE format('
                CREATE INDEX IF NOT EXISTS %I
                    ON %I.inventory_product_unit (company_id, product_id, purchase_reference_type, purchase_reference_id)
            ', 'idx_' || schema_name || '_inventory_product_unit_purchase_ref', schema_name);
        END IF;
    END LOOP;
END $$;
