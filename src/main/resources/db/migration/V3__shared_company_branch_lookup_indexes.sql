-- Shared lookup indexes for the stabilized company and branch read/provisioning paths.

CREATE INDEX IF NOT EXISTS idx_company_company_name
    ON public."Company" ("companyName");

CREATE INDEX IF NOT EXISTS idx_branch_branch_name
    ON public."Branch" ("branchName");

CREATE INDEX IF NOT EXISTS idx_branch_company_branch_name
    ON public."Branch" ("companyId", "branchName");
