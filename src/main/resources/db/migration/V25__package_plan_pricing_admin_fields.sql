ALTER TABLE public.package_plans
    ADD COLUMN IF NOT EXISTS monthly_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS currency_code TEXT NOT NULL DEFAULT 'EGP',
    ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.package_plans
    DROP CONSTRAINT IF EXISTS chk_package_plans_monthly_price_amount;

ALTER TABLE public.package_plans
    ADD CONSTRAINT chk_package_plans_monthly_price_amount
        CHECK (monthly_price_amount >= 0);

ALTER TABLE public.package_plans
    DROP CONSTRAINT IF EXISTS chk_package_plans_currency_code;

ALTER TABLE public.package_plans
    ADD CONSTRAINT chk_package_plans_currency_code
        CHECK (currency_code ~ '^[A-Z]{3}$');

UPDATE public.package_plans
SET
    monthly_price_amount = CASE package_id
        WHEN 'starter' THEN 160
        WHEN 'pro' THEN 240
        WHEN 'enterprise' THEN 320
        ELSE monthly_price_amount
    END,
    currency_code = 'EGP',
    display_order = CASE package_id
        WHEN 'starter' THEN 10
        WHEN 'pro' THEN 20
        WHEN 'enterprise' THEN 30
        ELSE display_order
    END,
    featured = CASE package_id
        WHEN 'pro' THEN TRUE
        ELSE FALSE
    END,
    updated_at = NOW()
WHERE package_id IN ('starter', 'pro', 'enterprise');
