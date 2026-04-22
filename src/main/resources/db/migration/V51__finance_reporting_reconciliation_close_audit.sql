CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.finance_account_balance (
    account_balance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    fiscal_period_id UUID NOT NULL,
    account_id UUID NOT NULL,
    branch_id INTEGER,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    opening_debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    opening_credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    period_debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    period_credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    closing_debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    closing_credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    last_rebuilt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_account_balance_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_account_balance_period
        FOREIGN KEY (company_id, fiscal_period_id)
        REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_account_balance_account
        FOREIGN KEY (company_id, account_id)
        REFERENCES public.finance_account (company_id, account_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_account_balance_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_account_balance_company_id
        UNIQUE (company_id, account_balance_id),
    CONSTRAINT chk_finance_account_balance_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_finance_account_balance_amounts
        CHECK (
            opening_debit >= 0
            AND opening_credit >= 0
            AND period_debit >= 0
            AND period_credit >= 0
            AND closing_debit >= 0
            AND closing_credit >= 0
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_account_balance_company_branch
    ON public.finance_account_balance (company_id, fiscal_period_id, account_id, branch_id, currency_code)
    WHERE branch_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_account_balance_company_total
    ON public.finance_account_balance (company_id, fiscal_period_id, account_id, currency_code)
    WHERE branch_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_finance_account_balance_period_account
    ON public.finance_account_balance (company_id, fiscal_period_id, account_id);

CREATE INDEX IF NOT EXISTS idx_finance_account_balance_period_branch
    ON public.finance_account_balance (company_id, fiscal_period_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_finance_account_balance_account
    ON public.finance_account_balance (company_id, account_id);

DROP TRIGGER IF EXISTS trg_finance_account_balance_set_updated_at
ON public.finance_account_balance;

CREATE TRIGGER trg_finance_account_balance_set_updated_at
BEFORE UPDATE ON public.finance_account_balance
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_trial_balance_snapshot (
    trial_balance_snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    fiscal_period_id UUID NOT NULL,
    snapshot_type VARCHAR(32) NOT NULL,
    includes_closing_entries BOOLEAN NOT NULL DEFAULT TRUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    generated_by INTEGER,
    total_debit DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_credit DECIMAL(19,4) NOT NULL DEFAULT 0,
    is_balanced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    CONSTRAINT fk_finance_trial_balance_snapshot_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_trial_balance_snapshot_period
        FOREIGN KEY (company_id, fiscal_period_id)
        REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_trial_balance_snapshot_company_id
        UNIQUE (company_id, trial_balance_snapshot_id),
    CONSTRAINT chk_finance_trial_balance_snapshot_type
        CHECK (snapshot_type IN ('pre_close', 'final_close', 'diagnostic')),
    CONSTRAINT chk_finance_trial_balance_snapshot_amounts
        CHECK (total_debit >= 0 AND total_credit >= 0),
    CONSTRAINT chk_finance_trial_balance_snapshot_balanced
        CHECK (is_balanced = FALSE OR total_debit = total_credit)
);

CREATE INDEX IF NOT EXISTS idx_finance_trial_balance_snapshot_period
    ON public.finance_trial_balance_snapshot (company_id, fiscal_period_id, snapshot_type, generated_at DESC);

CREATE TABLE IF NOT EXISTS public.finance_reconciliation_run (
    reconciliation_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    reconciliation_type VARCHAR(40) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'pending',
    difference_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    started_by INTEGER,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_reconciliation_run_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_reconciliation_run_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE CASCADE,
    CONSTRAINT uq_finance_reconciliation_run_company_id
        UNIQUE (company_id, reconciliation_run_id),
    CONSTRAINT chk_finance_reconciliation_run_type
        CHECK (reconciliation_type IN ('cash_drawer', 'card_settlement', 'bank', 'inventory_valuation', 'supplier', 'customer')),
    CONSTRAINT chk_finance_reconciliation_run_dates
        CHECK (period_start <= period_end),
    CONSTRAINT chk_finance_reconciliation_run_status
        CHECK (status IN ('pending', 'running', 'completed', 'completed_with_exceptions', 'failed', 'cancelled')),
    CONSTRAINT chk_finance_reconciliation_run_difference
        CHECK (difference_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_run_type_status
    ON public.finance_reconciliation_run (company_id, reconciliation_type, status);

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_run_branch_period
    ON public.finance_reconciliation_run (company_id, branch_id, period_start, period_end);

DROP TRIGGER IF EXISTS trg_finance_reconciliation_run_set_updated_at
ON public.finance_reconciliation_run;

CREATE TRIGGER trg_finance_reconciliation_run_set_updated_at
BEFORE UPDATE ON public.finance_reconciliation_run
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_reconciliation_item (
    reconciliation_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    reconciliation_run_id UUID NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    ledger_line_id UUID,
    match_status VARCHAR(40) NOT NULL DEFAULT 'unmatched_source',
    difference_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    resolution_status VARCHAR(40) NOT NULL DEFAULT 'unresolved',
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_reconciliation_item_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_reconciliation_item_run
        FOREIGN KEY (company_id, reconciliation_run_id)
        REFERENCES public.finance_reconciliation_run (company_id, reconciliation_run_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_reconciliation_item_ledger_line
        FOREIGN KEY (company_id, ledger_line_id)
        REFERENCES public.finance_journal_line (company_id, journal_line_id)
        ON DELETE SET NULL,
    CONSTRAINT uq_finance_reconciliation_item_company_id
        UNIQUE (company_id, reconciliation_item_id),
    CONSTRAINT chk_finance_reconciliation_item_match_status
        CHECK (match_status IN ('matched', 'unmatched_source', 'unmatched_ledger', 'difference', 'ignored')),
    CONSTRAINT chk_finance_reconciliation_item_resolution_status
        CHECK (resolution_status IN ('unresolved', 'proposed', 'resolved', 'dismissed')),
    CONSTRAINT chk_finance_reconciliation_item_difference
        CHECK (difference_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_item_run_match
    ON public.finance_reconciliation_item (company_id, reconciliation_run_id, match_status);

CREATE INDEX IF NOT EXISTS idx_finance_reconciliation_item_resolution
    ON public.finance_reconciliation_item (company_id, resolution_status);

DROP TRIGGER IF EXISTS trg_finance_reconciliation_item_set_updated_at
ON public.finance_reconciliation_item;

CREATE TRIGGER trg_finance_reconciliation_item_set_updated_at
BEFORE UPDATE ON public.finance_reconciliation_item
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_period_close_run (
    period_close_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    fiscal_period_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    started_by INTEGER,
    started_at TIMESTAMPTZ,
    completed_by INTEGER,
    completed_at TIMESTAMPTZ,
    failure_reason TEXT,
    trial_balance_snapshot_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by INTEGER,
    CONSTRAINT fk_finance_period_close_run_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_period_close_run_period
        FOREIGN KEY (company_id, fiscal_period_id)
        REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_period_close_run_snapshot
        FOREIGN KEY (company_id, trial_balance_snapshot_id)
        REFERENCES public.finance_trial_balance_snapshot (company_id, trial_balance_snapshot_id)
        ON DELETE SET NULL,
    CONSTRAINT uq_finance_period_close_run_company_id
        UNIQUE (company_id, period_close_run_id),
    CONSTRAINT chk_finance_period_close_run_status
        CHECK (status IN ('pending', 'running', 'completed', 'failed', 'reopened'))
);

CREATE INDEX IF NOT EXISTS idx_finance_period_close_run_period_status
    ON public.finance_period_close_run (company_id, fiscal_period_id, status);

DROP TRIGGER IF EXISTS trg_finance_period_close_run_set_updated_at
ON public.finance_period_close_run;

CREATE TRIGGER trg_finance_period_close_run_set_updated_at
BEFORE UPDATE ON public.finance_period_close_run
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.finance_audit_event (
    audit_event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    actor_user_id INTEGER,
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(128),
    before_state JSONB,
    after_state JSONB,
    reason TEXT,
    correlation_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_finance_audit_event_company
        FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_audit_event_branch
        FOREIGN KEY (company_id, branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON DELETE SET NULL,
    CONSTRAINT uq_finance_audit_event_company_id
        UNIQUE (company_id, audit_event_id),
    CONSTRAINT chk_finance_audit_event_type
        CHECK (length(btrim(event_type)) > 0),
    CONSTRAINT chk_finance_audit_event_entity_type
        CHECK (length(btrim(entity_type)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_finance_audit_event_company_time
    ON public.finance_audit_event (company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_finance_audit_event_entity
    ON public.finance_audit_event (company_id, entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_finance_audit_event_type_time
    ON public.finance_audit_event (company_id, event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_finance_audit_event_correlation
    ON public.finance_audit_event (correlation_id);
