-- Additional shared-table indexes for the stabilized Phase 2 operational paths.

CREATE INDEX IF NOT EXISTS idx_company_subscription_branch_id
    ON public."CompanySubscription" ("branchId");

CREATE INDEX IF NOT EXISTS idx_company_subscription_end_time
    ON public."CompanySubscription" ("endTime");
