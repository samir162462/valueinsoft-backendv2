CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.finance_fiscal_year (
    fiscal_year_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    base_currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    status VARCHAR(32) NOT NULL DEFAULT 'planned',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_fiscal_year_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_fiscal_year_company_id
        UNIQUE (company_id, fiscal_year_id),
    CONSTRAINT uq_finance_fiscal_year_name
        UNIQUE (company_id, name),
    CONSTRAINT chk_finance_fiscal_year_dates
        CHECK (start_date <= end_date),
    CONSTRAINT chk_finance_fiscal_year_status
        CHECK (status IN ('planned', 'open', 'closing', 'closed', 'archived')),
    CONSTRAINT chk_finance_fiscal_year_currency
        CHECK (base_currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_fiscal_year_version
        CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_finance_fiscal_year_company_status
    ON public.finance_fiscal_year (company_id, status);

CREATE INDEX IF NOT EXISTS idx_finance_fiscal_year_company_dates
    ON public.finance_fiscal_year (company_id, start_date, end_date);

DROP TRIGGER IF EXISTS trg_finance_fiscal_year_set_updated_at
ON public.finance_fiscal_year;

CREATE TRIGGER trg_finance_fiscal_year_set_updated_at
BEFORE UPDATE ON public.finance_fiscal_year
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_fiscal_period (
    fiscal_period_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    fiscal_year_id UUID NOT NULL,
    period_number INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'planned',
    locked_at TIMESTAMPTZ,
    locked_by INTEGER,
    closed_at TIMESTAMPTZ,
    closed_by INTEGER,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_fiscal_period_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_fiscal_period_year
        FOREIGN KEY (company_id, fiscal_year_id)
        REFERENCES public.finance_fiscal_year (company_id, fiscal_year_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_fiscal_period_company_id
        UNIQUE (company_id, fiscal_period_id),
    CONSTRAINT uq_finance_fiscal_period_number
        UNIQUE (company_id, fiscal_year_id, period_number),
    CONSTRAINT chk_finance_fiscal_period_dates
        CHECK (start_date <= end_date),
    CONSTRAINT chk_finance_fiscal_period_number
        CHECK (period_number > 0),
    CONSTRAINT chk_finance_fiscal_period_status
        CHECK (status IN ('planned', 'open', 'soft_locked', 'hard_closed')),
    CONSTRAINT chk_finance_fiscal_period_version
        CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_finance_fiscal_period_company_year
    ON public.finance_fiscal_period (company_id, fiscal_year_id, period_number);

CREATE INDEX IF NOT EXISTS idx_finance_fiscal_period_company_status
    ON public.finance_fiscal_period (company_id, status);

CREATE INDEX IF NOT EXISTS idx_finance_fiscal_period_company_dates
    ON public.finance_fiscal_period (company_id, start_date, end_date);

DROP TRIGGER IF EXISTS trg_finance_fiscal_period_set_updated_at
ON public.finance_fiscal_period;

CREATE TRIGGER trg_finance_fiscal_period_set_updated_at
BEFORE UPDATE ON public.finance_fiscal_period
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_account (
    account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    normal_balance VARCHAR(16) NOT NULL,
    parent_account_id UUID,
    account_path VARCHAR(1000) NOT NULL,
    account_level INTEGER NOT NULL DEFAULT 0,
    is_postable BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    currency_code VARCHAR(3),
    requires_branch BOOLEAN NOT NULL DEFAULT FALSE,
    requires_customer BOOLEAN NOT NULL DEFAULT FALSE,
    requires_supplier BOOLEAN NOT NULL DEFAULT FALSE,
    requires_product BOOLEAN NOT NULL DEFAULT FALSE,
    requires_cost_center BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_account_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_account_parent
        FOREIGN KEY (company_id, parent_account_id)
        REFERENCES public.finance_account (company_id, account_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_finance_account_company_id
        UNIQUE (company_id, account_id),
    CONSTRAINT uq_finance_account_code
        UNIQUE (company_id, account_code),
    CONSTRAINT chk_finance_account_type
        CHECK (account_type IN ('asset', 'liability', 'equity', 'revenue', 'expense')),
    CONSTRAINT chk_finance_account_normal_balance
        CHECK (normal_balance IN ('debit', 'credit')),
    CONSTRAINT chk_finance_account_status
        CHECK (status IN ('active', 'inactive', 'archived')),
    CONSTRAINT chk_finance_account_level
        CHECK (account_level >= 0),
    CONSTRAINT chk_finance_account_path
        CHECK (length(btrim(account_path)) > 0),
    CONSTRAINT chk_finance_account_currency
        CHECK (currency_code IS NULL OR currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_account_version
        CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_finance_account_company_code
    ON public.finance_account (company_id, account_code);

CREATE INDEX IF NOT EXISTS idx_finance_account_company_type_status
    ON public.finance_account (company_id, account_type, status);

CREATE INDEX IF NOT EXISTS idx_finance_account_company_parent
    ON public.finance_account (company_id, parent_account_id);

CREATE INDEX IF NOT EXISTS idx_finance_account_company_path
    ON public.finance_account (company_id, account_path);

CREATE INDEX IF NOT EXISTS idx_finance_account_company_postable_status
    ON public.finance_account (company_id, is_postable, status);

DROP TRIGGER IF EXISTS trg_finance_account_set_updated_at
ON public.finance_account;

CREATE TRIGGER trg_finance_account_set_updated_at
BEFORE UPDATE ON public.finance_account
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_cost_center (
    cost_center_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_cost_center_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_cost_center_company_id
        UNIQUE (company_id, cost_center_id),
    CONSTRAINT uq_finance_cost_center_code
        UNIQUE (company_id, code),
    CONSTRAINT chk_finance_cost_center_status
        CHECK (status IN ('active', 'inactive', 'archived')),
    CONSTRAINT chk_finance_cost_center_version
        CHECK (version >= 1)
);

DROP TRIGGER IF EXISTS trg_finance_cost_center_set_updated_at
ON public.finance_cost_center;

CREATE TRIGGER trg_finance_cost_center_set_updated_at
BEFORE UPDATE ON public.finance_cost_center
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_account_mapping (
    account_mapping_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    mapping_key VARCHAR(120) NOT NULL,
    account_id UUID NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_account_mapping_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_account_mapping_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_account_mapping_account
        FOREIGN KEY (company_id, account_id)
        REFERENCES public.finance_account (company_id, account_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_finance_account_mapping_company_id
        UNIQUE (company_id, account_mapping_id),
    CONSTRAINT chk_finance_account_mapping_dates
        CHECK (effective_to IS NULL OR effective_from <= effective_to),
    CONSTRAINT chk_finance_account_mapping_priority
        CHECK (priority > 0),
    CONSTRAINT chk_finance_account_mapping_status
        CHECK (status IN ('active', 'inactive', 'archived')),
    CONSTRAINT chk_finance_account_mapping_key
        CHECK (mapping_key ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$'),
    CONSTRAINT chk_finance_account_mapping_version
        CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_finance_account_mapping_lookup
    ON public.finance_account_mapping (company_id, mapping_key, branch_id, effective_from);

CREATE INDEX IF NOT EXISTS idx_finance_account_mapping_account
    ON public.finance_account_mapping (company_id, account_id);

CREATE INDEX IF NOT EXISTS idx_finance_account_mapping_status
    ON public.finance_account_mapping (company_id, status);

DROP TRIGGER IF EXISTS trg_finance_account_mapping_set_updated_at
ON public.finance_account_mapping;

CREATE TRIGGER trg_finance_account_mapping_set_updated_at
BEFORE UPDATE ON public.finance_account_mapping
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_tax_code (
    tax_code_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    rate DECIMAL(9,6) NOT NULL DEFAULT 0,
    tax_type VARCHAR(32) NOT NULL,
    output_account_id UUID,
    input_account_id UUID,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_tax_code_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_tax_code_output_account
        FOREIGN KEY (company_id, output_account_id)
        REFERENCES public.finance_account (company_id, account_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_finance_tax_code_input_account
        FOREIGN KEY (company_id, input_account_id)
        REFERENCES public.finance_account (company_id, account_id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_finance_tax_code_company_id
        UNIQUE (company_id, tax_code_id),
    CONSTRAINT uq_finance_tax_code_effective
        UNIQUE (company_id, code, effective_from),
    CONSTRAINT chk_finance_tax_code_rate
        CHECK (rate >= 0),
    CONSTRAINT chk_finance_tax_code_type
        CHECK (tax_type IN ('sales_vat', 'purchase_vat', 'withholding', 'exempt', 'zero_rated')),
    CONSTRAINT chk_finance_tax_code_dates
        CHECK (effective_to IS NULL OR effective_from <= effective_to),
    CONSTRAINT chk_finance_tax_code_status
        CHECK (status IN ('active', 'inactive', 'archived')),
    CONSTRAINT chk_finance_tax_code_version
        CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_finance_tax_code_lookup
    ON public.finance_tax_code (company_id, code, effective_from);

CREATE INDEX IF NOT EXISTS idx_finance_tax_code_status
    ON public.finance_tax_code (company_id, status);

DROP TRIGGER IF EXISTS trg_finance_tax_code_set_updated_at
ON public.finance_tax_code;

CREATE TRIGGER trg_finance_tax_code_set_updated_at
BEFORE UPDATE ON public.finance_tax_code
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();
