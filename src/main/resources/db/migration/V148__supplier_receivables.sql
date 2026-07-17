-- Suppliers may also buy goods from the company. Keep those receivables in AR,
-- distinct from supplier AP and without reusing Client identifiers.

CREATE OR REPLACE FUNCTION public.ensure_supplier_receivables_for_tenant(target_schema TEXT, target_company_id INTEGER)
RETURNS VOID AS $$
BEGIN
    IF target_schema IS NULL OR target_schema !~ '^c_[0-9]+$' THEN
        RAISE EXCEPTION 'Invalid tenant schema: %', target_schema;
    END IF;

    EXECUTE format('ALTER TABLE %I.ar_open_item ALTER COLUMN client_id DROP NOT NULL', target_schema);
    EXECUTE format('ALTER TABLE %I.ar_open_item ADD COLUMN IF NOT EXISTS party_type VARCHAR(20) NOT NULL DEFAULT ''CLIENT''', target_schema);
    EXECUTE format('ALTER TABLE %I.ar_open_item ADD COLUMN IF NOT EXISTS supplier_id INTEGER', target_schema);
    EXECUTE format('ALTER TABLE %I.ar_open_item DROP CONSTRAINT IF EXISTS ar_oi_source_type_ck', target_schema);
    EXECUTE format('ALTER TABLE %I.ar_open_item DROP CONSTRAINT IF EXISTS ar_oi_party_identity_ck', target_schema);
    EXECUTE format('ALTER TABLE %I.ar_open_item ADD CONSTRAINT ar_oi_source_type_ck CHECK (source_type IN (''POS_ORDER'',''POS_SUPPLIER_ORDER'',''OPENING_BALANCE'',''ADJUSTMENT''))', target_schema);
    EXECUTE format('ALTER TABLE %I.ar_open_item ADD CONSTRAINT ar_oi_party_identity_ck CHECK ((party_type=''CLIENT'' AND client_id IS NOT NULL AND supplier_id IS NULL) OR (party_type=''SUPPLIER'' AND supplier_id IS NOT NULL AND client_id IS NULL))', target_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.ar_open_item (supplier_id,status,due_date) WHERE party_type=''SUPPLIER''', 'idx_' || target_schema || '_ar_oi_supplier_status_due', target_schema);

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ar_supplier_receipt (
            receipt_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL REFERENCES public."Company"(id),
            branch_id INTEGER NOT NULL REFERENCES public."Branch"("branchId"),
            supplier_id INTEGER NOT NULL,
            amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
            currency_code VARCHAR(5) NOT NULL,
            payment_method VARCHAR(30) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''POSTED'' CHECK (status IN (''POSTED'',''REVERSED'')),
            reference_type VARCHAR(40),
            reference_id VARCHAR(80),
            idempotency_key VARCHAR(160) NOT NULL,
            notes VARCHAR(255),
            created_by VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(company_id,idempotency_key)
        )', target_schema);

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.ar_supplier_receipt_allocation (
            allocation_id BIGSERIAL PRIMARY KEY,
            company_id INTEGER NOT NULL REFERENCES public."Company"(id),
            branch_id INTEGER NOT NULL REFERENCES public."Branch"("branchId"),
            supplier_id INTEGER NOT NULL,
            receipt_id BIGINT NOT NULL REFERENCES %I.ar_supplier_receipt(receipt_id) ON DELETE RESTRICT,
            open_item_id BIGINT NOT NULL REFERENCES %I.ar_open_item(open_item_id) ON DELETE RESTRICT,
            amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
            currency_code VARCHAR(5) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''POSTED'' CHECK (status IN (''POSTED'',''REVERSED'')),
            idempotency_key VARCHAR(180) NOT NULL,
            created_by VARCHAR(120),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(company_id,idempotency_key)
        )', target_schema, target_schema, target_schema);
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT id FROM public."Company" LOOP
        IF to_regnamespace('c_' || c.id) IS NOT NULL
           AND to_regclass(format('%I.ar_open_item', 'c_' || c.id)) IS NOT NULL THEN
            PERFORM public.ensure_supplier_receivables_for_tenant('c_' || c.id, c.id);
        END IF;
    END LOOP;
END $$;
