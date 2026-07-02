DROP INDEX IF EXISTS public.idx_company_subscription_branch_id;
DROP INDEX IF EXISTS public.idx_company_subscription_end_time;
DROP INDEX IF EXISTS public.idx_company_subscription_order_id;
DROP INDEX IF EXISTS public.idx_company_subscription_order_status;
DROP INDEX IF EXISTS public.idx_company_subscription_branch_status_sid;

DROP TABLE IF EXISTS public."CompanySubscription";

ALTER TABLE public.branch_subscriptions
    DROP COLUMN IF EXISTS legacy_subscription_id;
