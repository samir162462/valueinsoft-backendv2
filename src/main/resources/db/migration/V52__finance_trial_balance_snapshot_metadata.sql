ALTER TABLE public.finance_trial_balance_snapshot
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP';

ALTER TABLE public.finance_trial_balance_snapshot
    ADD COLUMN IF NOT EXISTS balance_row_count BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_finance_trial_balance_snapshot_currency'
    ) THEN
        ALTER TABLE public.finance_trial_balance_snapshot
            ADD CONSTRAINT chk_finance_trial_balance_snapshot_currency
            CHECK (currency_code ~ '^[A-Z]{3}$');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_finance_trial_balance_snapshot_row_count'
    ) THEN
        ALTER TABLE public.finance_trial_balance_snapshot
            ADD CONSTRAINT chk_finance_trial_balance_snapshot_row_count
            CHECK (balance_row_count >= 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_finance_trial_balance_snapshot_period_currency
    ON public.finance_trial_balance_snapshot (company_id, fiscal_period_id, currency_code, snapshot_type, generated_at DESC);
