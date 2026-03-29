-- Phase 2 shared-schema alignment.
-- Keep this migration idempotent so Flyway can be enabled later without breaking
-- already-provisioned local environments.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'userPassword'
    ) THEN
        EXECUTE 'ALTER TABLE public.users ALTER COLUMN "userPassword" TYPE character varying(100)';
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_users_username ON public.users ("userName");
CREATE INDEX IF NOT EXISTS idx_users_email ON public.users ("userEmail");
CREATE INDEX IF NOT EXISTS idx_users_branch_id ON public.users ("branchId");
CREATE INDEX IF NOT EXISTS idx_company_owner_id ON public."Company" ("ownerId");
CREATE INDEX IF NOT EXISTS idx_branch_company_id ON public."Branch" ("companyId");
