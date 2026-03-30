CREATE UNIQUE INDEX IF NOT EXISTS uq_branch_company_branch_id
    ON public."Branch" ("companyId", "branchId");

CREATE SEQUENCE IF NOT EXISTS public.tenant_role_assignments_assignment_id_seq;

ALTER TABLE public.tenant_role_assignments
    ADD COLUMN IF NOT EXISTS assignment_id BIGINT;

ALTER SEQUENCE public.tenant_role_assignments_assignment_id_seq
    OWNED BY public.tenant_role_assignments.assignment_id;

ALTER TABLE public.tenant_role_assignments
    ALTER COLUMN assignment_id SET DEFAULT nextval('public.tenant_role_assignments_assignment_id_seq');

UPDATE public.tenant_role_assignments
SET assignment_id = nextval('public.tenant_role_assignments_assignment_id_seq')
WHERE assignment_id IS NULL;

ALTER TABLE public.tenant_role_assignments
    ALTER COLUMN assignment_id SET NOT NULL;

ALTER TABLE public.tenant_role_assignments
    ADD COLUMN IF NOT EXISTS scope_type TEXT;

UPDATE public.tenant_role_assignments
SET scope_type = 'company'
WHERE scope_type IS NULL;

ALTER TABLE public.tenant_role_assignments
    ALTER COLUMN scope_type SET NOT NULL;

ALTER TABLE public.tenant_role_assignments
    ADD COLUMN IF NOT EXISTS scope_branch_id INTEGER;

ALTER TABLE public.tenant_role_assignments
    DROP CONSTRAINT IF EXISTS tenant_role_assignments_pkey;

ALTER TABLE public.tenant_role_assignments
    DROP CONSTRAINT IF EXISTS pk_tenant_role_assignments;

ALTER TABLE public.tenant_role_assignments
    DROP CONSTRAINT IF EXISTS chk_tenant_role_assignments_scope_type;

ALTER TABLE public.tenant_role_assignments
    DROP CONSTRAINT IF EXISTS chk_tenant_role_assignments_scope_branch;

ALTER TABLE public.tenant_role_assignments
    DROP CONSTRAINT IF EXISTS fk_tenant_role_assignments_scope_branch;

ALTER TABLE public.tenant_role_assignments
    ADD CONSTRAINT pk_tenant_role_assignments PRIMARY KEY (assignment_id);

ALTER TABLE public.tenant_role_assignments
    ADD CONSTRAINT chk_tenant_role_assignments_scope_type
        CHECK (scope_type IN ('company', 'branch'));

ALTER TABLE public.tenant_role_assignments
    ADD CONSTRAINT chk_tenant_role_assignments_scope_branch
        CHECK (
            (scope_type = 'branch' AND scope_branch_id IS NOT NULL)
            OR
            (scope_type = 'company' AND scope_branch_id IS NULL)
        );

ALTER TABLE public.tenant_role_assignments
    ADD CONSTRAINT fk_tenant_role_assignments_scope_branch
        FOREIGN KEY (tenant_id, scope_branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON UPDATE CASCADE
        ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_tenant_role_assignments_scope_branch_id
    ON public.tenant_role_assignments (scope_branch_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_role_assignments_company_scope
    ON public.tenant_role_assignments (tenant_id, user_id, role_id, scope_type)
    WHERE scope_branch_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_role_assignments_branch_scope
    ON public.tenant_role_assignments (tenant_id, user_id, role_id, scope_type, scope_branch_id)
    WHERE scope_branch_id IS NOT NULL;

COMMENT ON COLUMN public.tenant_role_assignments.scope_type IS
    'Assignment scope level. company means tenant-wide; branch means restricted to one branch.';

COMMENT ON COLUMN public.tenant_role_assignments.scope_branch_id IS
    'Concrete branch target when scope_type is branch.';

CREATE SEQUENCE IF NOT EXISTS public.tenant_user_grant_overrides_override_id_seq;

ALTER TABLE public.tenant_user_grant_overrides
    ADD COLUMN IF NOT EXISTS override_id BIGINT;

ALTER SEQUENCE public.tenant_user_grant_overrides_override_id_seq
    OWNED BY public.tenant_user_grant_overrides.override_id;

ALTER TABLE public.tenant_user_grant_overrides
    ALTER COLUMN override_id SET DEFAULT nextval('public.tenant_user_grant_overrides_override_id_seq');

UPDATE public.tenant_user_grant_overrides
SET override_id = nextval('public.tenant_user_grant_overrides_override_id_seq')
WHERE override_id IS NULL;

ALTER TABLE public.tenant_user_grant_overrides
    ALTER COLUMN override_id SET NOT NULL;

ALTER TABLE public.tenant_user_grant_overrides
    ADD COLUMN IF NOT EXISTS scope_branch_id INTEGER;

ALTER TABLE public.tenant_user_grant_overrides
    DROP CONSTRAINT IF EXISTS tenant_user_grant_overrides_pkey;

ALTER TABLE public.tenant_user_grant_overrides
    DROP CONSTRAINT IF EXISTS pk_tenant_user_grant_overrides;

ALTER TABLE public.tenant_user_grant_overrides
    DROP CONSTRAINT IF EXISTS chk_tenant_user_grant_overrides_scope_branch;

ALTER TABLE public.tenant_user_grant_overrides
    DROP CONSTRAINT IF EXISTS fk_tenant_user_grant_overrides_scope_branch;

ALTER TABLE public.tenant_user_grant_overrides
    ADD CONSTRAINT pk_tenant_user_grant_overrides PRIMARY KEY (override_id);

ALTER TABLE public.tenant_user_grant_overrides
    ADD CONSTRAINT chk_tenant_user_grant_overrides_scope_branch
        CHECK (
            (scope_type = 'branch' AND scope_branch_id IS NOT NULL)
            OR
            (scope_type IN ('self', 'company', 'global_admin') AND scope_branch_id IS NULL)
        );

ALTER TABLE public.tenant_user_grant_overrides
    ADD CONSTRAINT fk_tenant_user_grant_overrides_scope_branch
        FOREIGN KEY (tenant_id, scope_branch_id)
        REFERENCES public."Branch" ("companyId", "branchId")
        ON UPDATE CASCADE
        ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_tenant_user_grant_overrides_scope_branch_id
    ON public.tenant_user_grant_overrides (scope_branch_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_user_grant_overrides_non_branch_scope
    ON public.tenant_user_grant_overrides (tenant_id, user_id, capability_key, scope_type)
    WHERE scope_branch_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_user_grant_overrides_branch_scope
    ON public.tenant_user_grant_overrides (tenant_id, user_id, capability_key, scope_type, scope_branch_id)
    WHERE scope_branch_id IS NOT NULL;

COMMENT ON COLUMN public.tenant_user_grant_overrides.scope_branch_id IS
    'Concrete branch target when a user grant override applies to branch scope.';
