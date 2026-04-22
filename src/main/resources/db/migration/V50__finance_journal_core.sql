CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.finance_journal_sequence (
    journal_sequence_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    sequence_key VARCHAR(80) NOT NULL,
    fiscal_year_id UUID,
    prefix VARCHAR(40) NOT NULL DEFAULT '',
    next_number BIGINT NOT NULL DEFAULT 1,
    padding INTEGER NOT NULL DEFAULT 6,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_journal_sequence_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_journal_sequence_year
        FOREIGN KEY (company_id, fiscal_year_id)
        REFERENCES public.finance_fiscal_year (company_id, fiscal_year_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_finance_journal_sequence_company_id
        UNIQUE (company_id, journal_sequence_id),
    CONSTRAINT chk_finance_journal_sequence_key
        CHECK (sequence_key ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$'),
    CONSTRAINT chk_finance_journal_sequence_next
        CHECK (next_number > 0),
    CONSTRAINT chk_finance_journal_sequence_padding
        CHECK (padding BETWEEN 1 AND 20),
    CONSTRAINT chk_finance_journal_sequence_version
        CHECK (version >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_journal_sequence_year_scoped
    ON public.finance_journal_sequence (company_id, sequence_key, fiscal_year_id)
    WHERE fiscal_year_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_journal_sequence_company_scoped
    ON public.finance_journal_sequence (company_id, sequence_key)
    WHERE fiscal_year_id IS NULL;

DROP TRIGGER IF EXISTS trg_finance_journal_sequence_set_updated_at
ON public.finance_journal_sequence;

CREATE TRIGGER trg_finance_journal_sequence_set_updated_at
BEFORE UPDATE ON public.finance_journal_sequence
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_posting_batch (
    posting_batch_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    batch_type VARCHAR(64) NOT NULL,
    source_module VARCHAR(64) NOT NULL,
    source_reference VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    requested_by INTEGER,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_posting_batch_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_posting_batch_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_posting_batch_company_id
        UNIQUE (company_id, posting_batch_id),
    CONSTRAINT chk_finance_posting_batch_status
        CHECK (status IN ('pending', 'processing', 'posted', 'failed', 'partially_posted', 'cancelled')),
    CONSTRAINT chk_finance_posting_batch_type
        CHECK (length(btrim(batch_type)) > 0),
    CONSTRAINT chk_finance_posting_batch_module
        CHECK (length(btrim(source_module)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_finance_posting_batch_status
    ON public.finance_posting_batch (company_id, status, requested_at);

CREATE INDEX IF NOT EXISTS idx_finance_posting_batch_source
    ON public.finance_posting_batch (company_id, source_module, source_reference);

DROP TRIGGER IF EXISTS trg_finance_posting_batch_set_updated_at
ON public.finance_posting_batch;

CREATE TRIGGER trg_finance_posting_batch_set_updated_at
BEFORE UPDATE ON public.finance_posting_batch
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_journal_entry (
    journal_entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    journal_number VARCHAR(120) NOT NULL,
    journal_type VARCHAR(40) NOT NULL,
    source_module VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    posting_date DATE NOT NULL,
    fiscal_period_id UUID NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    exchange_rate DECIMAL(19,8) NOT NULL DEFAULT 1.00000000,
    total_debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    is_closing_entry BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at TIMESTAMPTZ,
    posted_by INTEGER,
    reversal_of_journal_id UUID,
    reversed_by_journal_id UUID,
    posting_batch_id UUID,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_journal_entry_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_journal_entry_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_entry_period
        FOREIGN KEY (company_id, fiscal_period_id)
        REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_entry_reversal_of
        FOREIGN KEY (company_id, reversal_of_journal_id)
        REFERENCES public.finance_journal_entry (company_id, journal_entry_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_entry_reversed_by
        FOREIGN KEY (company_id, reversed_by_journal_id)
        REFERENCES public.finance_journal_entry (company_id, journal_entry_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_entry_batch
        FOREIGN KEY (company_id, posting_batch_id)
        REFERENCES public.finance_posting_batch (company_id, posting_batch_id)
        ON DELETE SET NULL,
    CONSTRAINT uq_finance_journal_entry_company_id
        UNIQUE (company_id, journal_entry_id),
    CONSTRAINT uq_finance_journal_entry_number
        UNIQUE (company_id, journal_number),
    CONSTRAINT chk_finance_journal_entry_type
        CHECK (journal_type IN ('sales', 'sales_return', 'purchase', 'purchase_return', 'payment', 'inventory', 'adjustment', 'reversal', 'opening_balance', 'closing')),
    CONSTRAINT chk_finance_journal_entry_source_module
        CHECK (source_module IN ('pos', 'purchase', 'inventory', 'payment', 'manual', 'system', 'migration')),
    CONSTRAINT chk_finance_journal_entry_status
        CHECK (status IN ('draft', 'validated', 'posted', 'reversed', 'voided')),
    CONSTRAINT chk_finance_journal_entry_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_journal_entry_exchange_rate
        CHECK (exchange_rate > 0),
    CONSTRAINT chk_finance_journal_entry_amounts
        CHECK (total_debit >= 0 AND total_credit >= 0),
    CONSTRAINT chk_finance_journal_entry_posted_balanced
        CHECK (status <> 'posted' OR total_debit = total_credit),
    CONSTRAINT chk_finance_journal_entry_version
        CHECK (version >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_journal_entry_source
    ON public.finance_journal_entry (company_id, source_module, source_type, source_id)
    WHERE source_id IS NOT NULL AND status IN ('validated', 'posted', 'reversed');

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_journal_entry_reversed_by
    ON public.finance_journal_entry (company_id, reversed_by_journal_id)
    WHERE reversed_by_journal_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_finance_journal_entry_posting_date
    ON public.finance_journal_entry (company_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_entry_period_status
    ON public.finance_journal_entry (company_id, fiscal_period_id, status);

CREATE INDEX IF NOT EXISTS idx_finance_journal_entry_branch_date
    ON public.finance_journal_entry (company_id, branch_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_entry_source
    ON public.finance_journal_entry (company_id, source_module, source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_finance_journal_entry_closing
    ON public.finance_journal_entry (company_id, is_closing_entry, fiscal_period_id);

DROP TRIGGER IF EXISTS trg_finance_journal_entry_set_updated_at
ON public.finance_journal_entry;

CREATE TRIGGER trg_finance_journal_entry_set_updated_at
BEFORE UPDATE ON public.finance_journal_entry
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_posting_request (
    posting_request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    posting_batch_id UUID,
    source_module VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    journal_entry_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_posting_request_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_posting_request_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_posting_request_batch
        FOREIGN KEY (company_id, posting_batch_id)
        REFERENCES public.finance_posting_batch (company_id, posting_batch_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_finance_posting_request_journal
        FOREIGN KEY (company_id, journal_entry_id)
        REFERENCES public.finance_journal_entry (company_id, journal_entry_id)
        ON DELETE SET NULL,
    CONSTRAINT uq_finance_posting_request_company_id
        UNIQUE (company_id, posting_request_id),
    CONSTRAINT uq_finance_posting_request_source
        UNIQUE (company_id, source_module, source_type, source_id),
    CONSTRAINT chk_finance_posting_request_status
        CHECK (status IN ('pending', 'processing', 'posted', 'failed', 'ignored', 'cancelled')),
    CONSTRAINT chk_finance_posting_request_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_finance_posting_request_hash
        CHECK (length(btrim(request_hash)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_finance_posting_request_status
    ON public.finance_posting_request (company_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_finance_posting_request_source
    ON public.finance_posting_request (company_id, source_module, source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_finance_posting_request_journal
    ON public.finance_posting_request (company_id, journal_entry_id);

DROP TRIGGER IF EXISTS trg_finance_posting_request_set_updated_at
ON public.finance_posting_request;

CREATE TRIGGER trg_finance_posting_request_set_updated_at
BEFORE UPDATE ON public.finance_posting_request
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_journal_line (
    journal_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    journal_entry_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    account_id UUID NOT NULL,
    branch_id INTEGER,
    posting_date DATE NOT NULL,
    fiscal_period_id UUID NOT NULL,
    debit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    exchange_rate DECIMAL(19,8) NOT NULL DEFAULT 1.00000000,
    foreign_debit_amount DECIMAL(19,4),
    foreign_credit_amount DECIMAL(19,4),
    description TEXT,
    customer_id INTEGER,
    supplier_id INTEGER,
    product_id BIGINT,
    inventory_movement_id BIGINT,
    payment_id VARCHAR(128),
    cost_center_id UUID,
    tax_code_id UUID,
    source_module VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    CONSTRAINT fk_finance_journal_line_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_journal_line_journal
        FOREIGN KEY (company_id, journal_entry_id)
        REFERENCES public.finance_journal_entry (company_id, journal_entry_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_journal_line_account
        FOREIGN KEY (company_id, account_id)
        REFERENCES public.finance_account (company_id, account_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_line_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_line_period
        FOREIGN KEY (company_id, fiscal_period_id)
        REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_line_cost_center
        FOREIGN KEY (company_id, cost_center_id)
        REFERENCES public.finance_cost_center (company_id, cost_center_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_journal_line_tax_code
        FOREIGN KEY (company_id, tax_code_id)
        REFERENCES public.finance_tax_code (company_id, tax_code_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_finance_journal_line_company_id
        UNIQUE (company_id, journal_line_id),
    CONSTRAINT uq_finance_journal_line_number
        UNIQUE (company_id, journal_entry_id, line_number),
    CONSTRAINT chk_finance_journal_line_number
        CHECK (line_number > 0),
    CONSTRAINT chk_finance_journal_line_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_journal_line_exchange_rate
        CHECK (exchange_rate > 0),
    CONSTRAINT chk_finance_journal_line_amounts
        CHECK (debit_amount >= 0 AND credit_amount >= 0),
    CONSTRAINT chk_finance_journal_line_debit_credit
        CHECK (
            (debit_amount > 0 AND credit_amount = 0)
            OR (credit_amount > 0 AND debit_amount = 0)
        ),
    CONSTRAINT chk_finance_journal_line_foreign_amounts
        CHECK (
            (foreign_debit_amount IS NULL OR foreign_debit_amount >= 0)
            AND (foreign_credit_amount IS NULL OR foreign_credit_amount >= 0)
        )
);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_account_date
    ON public.finance_journal_line (company_id, account_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_period_account
    ON public.finance_journal_line (company_id, fiscal_period_id, account_id);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_branch_date
    ON public.finance_journal_line (company_id, branch_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_customer_date
    ON public.finance_journal_line (company_id, customer_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_supplier_date
    ON public.finance_journal_line (company_id, supplier_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_product_date
    ON public.finance_journal_line (company_id, product_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_journal_line_source
    ON public.finance_journal_line (company_id, source_module, source_type, source_id);

CREATE TABLE IF NOT EXISTS public.finance_tax_line (
    tax_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    journal_entry_id UUID NOT NULL,
    journal_line_id UUID,
    tax_code_id UUID NOT NULL,
    posting_date DATE NOT NULL,
    fiscal_period_id UUID NOT NULL,
    tax_direction VARCHAR(16) NOT NULL,
    taxable_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    source_module VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    CONSTRAINT fk_finance_tax_line_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_tax_line_journal
        FOREIGN KEY (company_id, journal_entry_id)
        REFERENCES public.finance_journal_entry (company_id, journal_entry_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_tax_line_journal_line
        FOREIGN KEY (company_id, journal_line_id)
        REFERENCES public.finance_journal_line (company_id, journal_line_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_tax_line_tax_code
        FOREIGN KEY (company_id, tax_code_id)
        REFERENCES public.finance_tax_code (company_id, tax_code_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_tax_line_period
        FOREIGN KEY (company_id, fiscal_period_id)
        REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_finance_tax_line_company_id
        UNIQUE (company_id, tax_line_id),
    CONSTRAINT chk_finance_tax_line_direction
        CHECK (tax_direction IN ('output', 'input')),
    CONSTRAINT chk_finance_tax_line_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_tax_line_amounts
        CHECK (taxable_amount >= 0 AND tax_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_finance_tax_line_code_date
    ON public.finance_tax_line (company_id, tax_code_id, posting_date);

CREATE INDEX IF NOT EXISTS idx_finance_tax_line_period_direction
    ON public.finance_tax_line (company_id, fiscal_period_id, tax_direction);

CREATE INDEX IF NOT EXISTS idx_finance_tax_line_source
    ON public.finance_tax_line (company_id, source_module, source_type, source_id);
