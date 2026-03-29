-- Shared lookup indexes for the stabilized subscription payment and callback paths.

CREATE INDEX IF NOT EXISTS idx_company_subscription_order_id
    ON public."CompanySubscription" (order_id);

CREATE INDEX IF NOT EXISTS idx_company_subscription_order_status
    ON public."CompanySubscription" (order_id, status);

CREATE INDEX IF NOT EXISTS idx_company_subscription_branch_status_sid
    ON public."CompanySubscription" ("branchId", status, "sId");
