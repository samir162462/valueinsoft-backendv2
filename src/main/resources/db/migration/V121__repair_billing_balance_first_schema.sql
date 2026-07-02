ALTER TABLE public.billing_accounts
    ADD COLUMN IF NOT EXISTS available_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_accounts'::regclass
          AND conname = 'uq_billing_accounts_company'
    ) THEN
        ALTER TABLE public.billing_accounts
            DROP CONSTRAINT uq_billing_accounts_company;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_accounts'::regclass
          AND conname = 'uq_billing_accounts_company_currency'
    ) THEN
        ALTER TABLE public.billing_accounts
            ADD CONSTRAINT uq_billing_accounts_company_currency UNIQUE (company_id, currency_code);
    END IF;
END $$;

ALTER TABLE public.billing_invoices
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE public.billing_invoices
SET paid_amount = GREATEST(COALESCE(total_amount, 0) - COALESCE(due_amount, 0), 0)
WHERE paid_amount = 0
  AND COALESCE(total_amount, 0) > 0
  AND COALESCE(due_amount, 0) >= 0;

CREATE TABLE IF NOT EXISTS public.billing_account_ledger (
    billing_account_ledger_id BIGSERIAL PRIMARY KEY,
    billing_account_id BIGINT NOT NULL,
    company_id INTEGER NOT NULL,
    transaction_type VARCHAR(64) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    direction VARCHAR(16) NOT NULL,
    balance_before NUMERIC(12, 2) NOT NULL,
    balance_after NUMERIC(12, 2) NOT NULL,
    reference_type VARCHAR(64),
    reference_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    funding_source VARCHAR(64),
    credit_reason VARCHAR(64),
    approval_status VARCHAR(32),
    approved_by INTEGER,
    approved_at TIMESTAMPTZ,
    description TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_account_ledger_account FOREIGN KEY (billing_account_id)
        REFERENCES public.billing_accounts (billing_account_id),
    CONSTRAINT fk_billing_account_ledger_company FOREIGN KEY (company_id)
        REFERENCES public."Company" (id),
    CONSTRAINT chk_billing_account_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_billing_account_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT uq_billing_account_ledger_company_idempotency UNIQUE (company_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_billing_account_ledger_account_created
    ON public.billing_account_ledger (billing_account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_account_ledger_company_created
    ON public.billing_account_ledger (company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_account_ledger_reference
    ON public.billing_account_ledger (reference_type, reference_id)
    WHERE reference_type IS NOT NULL AND reference_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.billing_payments (
    billing_payment_id BIGSERIAL PRIMARY KEY,
    company_id INTEGER NOT NULL,
    billing_account_id BIGINT NOT NULL,
    payment_source VARCHAR(64) NOT NULL,
    provider_code VARCHAR(50),
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    provider_reference VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_payments_company FOREIGN KEY (company_id)
        REFERENCES public."Company" (id),
    CONSTRAINT fk_billing_payments_account FOREIGN KEY (billing_account_id)
        REFERENCES public.billing_accounts (billing_account_id),
    CONSTRAINT chk_billing_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_billing_payments_status CHECK (status IN ('CREATED', 'ALLOCATED', 'FAILED', 'REVERSED')),
    CONSTRAINT uq_billing_payments_company_idempotency UNIQUE (company_id, idempotency_key)
);

ALTER TABLE public.billing_payments
    ADD COLUMN IF NOT EXISTS provider_gross_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS provider_fee_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS provider_net_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS provider_settlement_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS settlement_destination VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_billing_payments_company_source_created
    ON public.billing_payments (company_id, payment_source, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_payments_provider_reference
    ON public.billing_payments (provider_code, provider_reference)
    WHERE provider_code IS NOT NULL AND provider_reference IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_payments_reconciliation_status
    ON public.billing_payments (company_id, reconciliation_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_payments_provider_settlement_reference
    ON public.billing_payments (provider_code, provider_settlement_reference)
    WHERE provider_code IS NOT NULL AND provider_settlement_reference IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.billing_payment_allocations (
    billing_payment_allocation_id BIGSERIAL PRIMARY KEY,
    billing_payment_id BIGINT NOT NULL,
    billing_invoice_id BIGINT NOT NULL,
    allocated_amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_payment_allocations_payment FOREIGN KEY (billing_payment_id)
        REFERENCES public.billing_payments (billing_payment_id),
    CONSTRAINT fk_billing_payment_allocations_invoice FOREIGN KEY (billing_invoice_id)
        REFERENCES public.billing_invoices (billing_invoice_id),
    CONSTRAINT chk_billing_payment_allocations_amount CHECK (allocated_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_billing_payment_allocations_invoice_created
    ON public.billing_payment_allocations (billing_invoice_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_payment_allocations_payment
    ON public.billing_payment_allocations (billing_payment_id);
