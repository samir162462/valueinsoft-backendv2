-- Shared lookup indexes for the stabilized user-admin and branch-user listing paths.

CREATE INDEX IF NOT EXISTS idx_users_branch_creation_time
    ON public.users ("branchId", "creationTime");

CREATE INDEX IF NOT EXISTS idx_users_branch_role
    ON public.users ("branchId", "userRole");
