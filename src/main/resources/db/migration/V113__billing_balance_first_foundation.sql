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

CREATE INDEX IF NOT EXISTS idx_billing_payments_company_source_created
    ON public.billing_payments (company_id, payment_source, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_payments_provider_reference
    ON public.billing_payments (provider_code, provider_reference)
    WHERE provider_code IS NOT NULL AND provider_reference IS NOT NULL;

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

ALTER TABLE public.billing_payment_attempts
    ADD COLUMN IF NOT EXISTS company_id INTEGER,
    ADD COLUMN IF NOT EXISTS branch_id INTEGER,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS external_intention_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS checkout_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS checkout_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS superseded_by_attempt_id BIGINT,
    ADD COLUMN IF NOT EXISTS terminal_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE public.billing_payment_attempts bpa
SET company_id = ba.company_id,
    branch_id = bs.branch_id
FROM public.billing_invoices bi
JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id
LEFT JOIN public.branch_subscriptions bs
    ON bi.source_type = 'branch_subscription'
   AND bi.source_id = bs.branch_subscription_id::text
WHERE bpa.billing_invoice_id = bi.billing_invoice_id
  AND bpa.company_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_payment_attempts'::regclass
          AND conname = 'fk_billing_payment_attempts_company'
    ) THEN
        ALTER TABLE public.billing_payment_attempts
            ADD CONSTRAINT fk_billing_payment_attempts_company FOREIGN KEY (company_id)
                REFERENCES public."Company" (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_payment_attempts'::regclass
          AND conname = 'fk_billing_payment_attempts_branch'
    ) THEN
        ALTER TABLE public.billing_payment_attempts
            ADD CONSTRAINT fk_billing_payment_attempts_branch FOREIGN KEY (branch_id)
                REFERENCES public."Branch" ("branchId");
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_payment_attempts'::regclass
          AND conname = 'fk_billing_payment_attempts_superseded_by'
    ) THEN
        ALTER TABLE public.billing_payment_attempts
            ADD CONSTRAINT fk_billing_payment_attempts_superseded_by FOREIGN KEY (superseded_by_attempt_id)
                REFERENCES public.billing_payment_attempts (billing_payment_attempt_id);
    END IF;
END $$;

WITH duplicate_attempts AS (
    SELECT billing_payment_attempt_id,
           external_order_id,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(provider_code), external_order_id
               ORDER BY billing_payment_attempt_id DESC
           ) AS duplicate_rank
    FROM public.billing_payment_attempts
    WHERE external_order_id IS NOT NULL
      AND BTRIM(external_order_id) <> ''
)
UPDATE public.billing_payment_attempts bpa
SET external_order_id = duplicate_attempts.external_order_id || ':duplicate:' || bpa.billing_payment_attempt_id
FROM duplicate_attempts
WHERE duplicate_attempts.billing_payment_attempt_id = bpa.billing_payment_attempt_id
  AND duplicate_attempts.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_provider_order
    ON public.billing_payment_attempts (LOWER(provider_code), external_order_id)
    WHERE external_order_id IS NOT NULL AND BTRIM(external_order_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_provider_intention
    ON public.billing_payment_attempts (LOWER(provider_code), external_intention_id)
    WHERE external_intention_id IS NOT NULL AND BTRIM(external_intention_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_company_idempotency
    ON public.billing_payment_attempts (company_id, idempotency_key)
    WHERE company_id IS NOT NULL AND idempotency_key IS NOT NULL AND BTRIM(idempotency_key) <> '';

WITH ranked_active_attempts AS (
    SELECT billing_payment_attempt_id,
           FIRST_VALUE(billing_payment_attempt_id) OVER (
               PARTITION BY billing_invoice_id, LOWER(provider_code)
               ORDER BY billing_payment_attempt_id DESC
           ) AS latest_attempt_id,
           ROW_NUMBER() OVER (
               PARTITION BY billing_invoice_id, LOWER(provider_code)
               ORDER BY billing_payment_attempt_id DESC
           ) AS active_rank
    FROM public.billing_payment_attempts
    WHERE UPPER(status) IN ('CREATED', 'CHECKOUT_PENDING', 'CHECKOUT_REQUESTED', 'PENDING_PROVIDER')
)
UPDATE public.billing_payment_attempts bpa
SET status = 'SUPERSEDED',
    superseded_by_attempt_id = ranked_active_attempts.latest_attempt_id,
    terminal_at = COALESCE(terminal_at, NOW())
FROM ranked_active_attempts
WHERE ranked_active_attempts.billing_payment_attempt_id = bpa.billing_payment_attempt_id
  AND ranked_active_attempts.active_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_active_invoice_provider
    ON public.billing_payment_attempts (billing_invoice_id, LOWER(provider_code))
    WHERE UPPER(status) IN ('CREATED', 'CHECKOUT_PENDING', 'CHECKOUT_REQUESTED', 'PENDING_PROVIDER');

CREATE INDEX IF NOT EXISTS idx_billing_payment_attempts_company_status
    ON public.billing_payment_attempts (company_id, status, attempted_at DESC)
    WHERE company_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_payment_attempts_branch_status
    ON public.billing_payment_attempts (branch_id, status, attempted_at DESC)
    WHERE branch_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.billing_provider_checkout_outbox (
    checkout_outbox_id BIGSERIAL PRIMARY KEY,
    billing_payment_attempt_id BIGINT NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_payload_json JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_provider_checkout_outbox_attempt FOREIGN KEY (billing_payment_attempt_id)
        REFERENCES public.billing_payment_attempts (billing_payment_attempt_id),
    CONSTRAINT uq_billing_provider_checkout_outbox_idempotency UNIQUE (provider_code, idempotency_key),
    CONSTRAINT chk_billing_provider_checkout_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_FINAL', 'FAILED_UNCERTAIN')
    ),
    CONSTRAINT chk_billing_provider_checkout_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_billing_provider_checkout_outbox_status
    ON public.billing_provider_checkout_outbox (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_billing_provider_checkout_outbox_attempt
    ON public.billing_provider_checkout_outbox (billing_payment_attempt_id);

DROP TRIGGER IF EXISTS trg_billing_provider_checkout_outbox_set_updated_at
ON public.billing_provider_checkout_outbox;

CREATE TRIGGER trg_billing_provider_checkout_outbox_set_updated_at
BEFORE UPDATE ON public.billing_provider_checkout_outbox
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

ALTER TABLE public.billing_provider_events
    ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS attempt_id BIGINT,
    ADD COLUMN IF NOT EXISTS billing_invoice_id BIGINT,
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE public.billing_provider_events
SET received_at = COALESCE(received_at, created_at, NOW());

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_provider_events'::regclass
          AND conname = 'fk_billing_provider_events_attempt'
    ) THEN
        ALTER TABLE public.billing_provider_events
            ADD CONSTRAINT fk_billing_provider_events_attempt FOREIGN KEY (attempt_id)
                REFERENCES public.billing_payment_attempts (billing_payment_attempt_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_provider_events'::regclass
          AND conname = 'fk_billing_provider_events_invoice'
    ) THEN
        ALTER TABLE public.billing_provider_events
            ADD CONSTRAINT fk_billing_provider_events_invoice FOREIGN KEY (billing_invoice_id)
                REFERENCES public.billing_invoices (billing_invoice_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.billing_provider_events'::regclass
          AND conname = 'fk_billing_provider_events_company'
    ) THEN
        ALTER TABLE public.billing_provider_events
            ADD CONSTRAINT fk_billing_provider_events_company FOREIGN KEY (company_id)
                REFERENCES public."Company" (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_billing_provider_events_company_status
    ON public.billing_provider_events (company_id, processing_status, received_at DESC)
    WHERE company_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_provider_events_attempt
    ON public.billing_provider_events (attempt_id)
    WHERE attempt_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_provider_events_invoice
    ON public.billing_provider_events (billing_invoice_id)
    WHERE billing_invoice_id IS NOT NULL;
