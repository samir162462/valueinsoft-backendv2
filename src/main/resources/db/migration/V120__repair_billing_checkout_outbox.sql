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
)
UPDATE public.billing_payment_attempts bpa
SET external_order_id = duplicate_attempts.external_order_id || ':duplicate:' || bpa.billing_payment_attempt_id
FROM duplicate_attempts
WHERE duplicate_attempts.billing_payment_attempt_id = bpa.billing_payment_attempt_id
  AND duplicate_attempts.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_provider_order
    ON public.billing_payment_attempts (LOWER(provider_code), external_order_id)
    WHERE external_order_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_provider_intention
    ON public.billing_payment_attempts (LOWER(provider_code), external_intention_id)
    WHERE external_intention_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_billing_payment_attempts_company_idempotency
    ON public.billing_payment_attempts (company_id, idempotency_key)
    WHERE company_id IS NOT NULL AND idempotency_key IS NOT NULL;

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
