ALTER TABLE public.finance_posting_request
    ADD COLUMN IF NOT EXISTS posting_date DATE;

ALTER TABLE public.finance_posting_request
    ADD COLUMN IF NOT EXISTS fiscal_period_id UUID;

ALTER TABLE public.finance_posting_request
    ADD COLUMN IF NOT EXISTS request_payload JSONB NOT NULL DEFAULT '{}'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_finance_posting_request_period'
    ) THEN
        ALTER TABLE public.finance_posting_request
            ADD CONSTRAINT fk_finance_posting_request_period
            FOREIGN KEY (company_id, fiscal_period_id)
            REFERENCES public.finance_fiscal_period (company_id, fiscal_period_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_finance_posting_request_period_status
    ON public.finance_posting_request (company_id, fiscal_period_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_finance_posting_request_posting_date
    ON public.finance_posting_request (company_id, posting_date, status);
