CREATE TABLE IF NOT EXISTS public.finance_reconciliation_source_item (
    reconciliation_source_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    reconciliation_type VARCHAR(40) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    external_reference VARCHAR(128) NOT NULL,
    source_date DATE NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    description TEXT,
    raw_payload JSONB,
    status VARCHAR(40) NOT NULL DEFAULT 'imported',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_reconciliation_source_item_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_reconciliation_source_item_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_reconciliation_source_item_company_id
        UNIQUE (company_id, reconciliation_source_item_id),
    CONSTRAINT uq_finance_reconciliation_source_item_external_ref
        UNIQUE (company_id, reconciliation_type, source_system, external_reference),
    CONSTRAINT chk_finance_reconciliation_source_item_type
        CHECK (reconciliation_type IN ('cash_drawer', 'card_settlement', 'bank', 'inventory_valuation', 'supplier', 'customer')),
    CONSTRAINT chk_finance_reconciliation_source_item_amount
        CHECK (amount >= 0),
    CONSTRAINT chk_finance_reconciliation_source_item_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_reconciliation_source_item_status
        CHECK (status IN ('imported', 'matched', 'exception', 'ignored'))
);

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_source_item_lookup
    ON public.finance_reconciliation_source_item
    (company_id, reconciliation_type, branch_id, source_date, status);

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_source_item_external_ref
    ON public.finance_reconciliation_source_item
    (company_id, reconciliation_type, source_system, external_reference);

DROP TRIGGER IF EXISTS trg_finance_reconciliation_source_item_set_updated_at
ON public.finance_reconciliation_source_item;

CREATE TRIGGER trg_finance_reconciliation_source_item_set_updated_at
BEFORE UPDATE ON public.finance_reconciliation_source_item
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

ALTER TABLE public.finance_reconciliation_item
    ADD COLUMN IF NOT EXISTS reconciliation_source_item_id UUID;

ALTER TABLE public.finance_reconciliation_item
    DROP CONSTRAINT IF EXISTS fk_finance_reconciliation_item_source_item;

ALTER TABLE public.finance_reconciliation_item
    ADD CONSTRAINT fk_finance_reconciliation_item_source_item
        FOREIGN KEY (company_id, reconciliation_source_item_id)
        REFERENCES public.finance_reconciliation_source_item (company_id, reconciliation_source_item_id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_item_source_item
    ON public.finance_reconciliation_item (company_id, reconciliation_source_item_id);
