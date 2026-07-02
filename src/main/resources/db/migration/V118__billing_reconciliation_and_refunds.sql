ALTER TABLE public.billing_payments
    ADD COLUMN IF NOT EXISTS provider_gross_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS provider_fee_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS provider_net_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS settlement_currency_code VARCHAR(3),
    ADD COLUMN IF NOT EXISTS settlement_destination VARCHAR(64),
    ADD COLUMN IF NOT EXISTS provider_settlement_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_billing_payments_reconciliation_status
    ON public.billing_payments (company_id, reconciliation_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_payments_provider_settlement_reference
    ON public.billing_payments (provider_code, provider_settlement_reference)
    WHERE provider_code IS NOT NULL AND provider_settlement_reference IS NOT NULL;
