CREATE TABLE IF NOT EXISTS public.tenants (
    tenant_id INTEGER PRIMARY KEY,
    package_id TEXT NOT NULL,
    template_id TEXT NOT NULL,
    status TEXT NOT NULL,
    config_version TEXT NOT NULL DEFAULT 'v1',
    legacy_plan_name TEXT NULL,
    bootstrap_source TEXT NOT NULL DEFAULT 'manual',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenants_company
        FOREIGN KEY (tenant_id)
        REFERENCES public."Company" (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_tenants_package
        FOREIGN KEY (package_id)
        REFERENCES public.package_plans (package_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_tenants_template
        FOREIGN KEY (template_id)
        REFERENCES public.company_templates (template_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_tenants_tenant_id
        CHECK (tenant_id > 0),
    CONSTRAINT chk_tenants_status
        CHECK (status IN ('onboarding', 'active', 'suspended', 'archived')),
    CONSTRAINT chk_tenants_config_version
        CHECK (length(btrim(config_version)) > 0),
    CONSTRAINT chk_tenants_bootstrap_source
        CHECK (bootstrap_source IN ('manual', 'legacy_migration', 'api', 'support', 'admin', 'bootstrap'))
);

COMMENT ON TABLE public.tenants IS
    'Tenant configuration root. tenant_id is anchored to the legacy Company.id identifier.';

CREATE INDEX IF NOT EXISTS idx_tenants_package_id
    ON public.tenants (package_id);

CREATE INDEX IF NOT EXISTS idx_tenants_template_id
    ON public.tenants (template_id);

CREATE INDEX IF NOT EXISTS idx_tenants_status
    ON public.tenants (status);

DROP TRIGGER IF EXISTS trg_tenants_set_updated_at
ON public.tenants;

CREATE TRIGGER trg_tenants_set_updated_at
BEFORE UPDATE ON public.tenants
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.tenant_module_overrides (
    tenant_id INTEGER NOT NULL,
    module_id TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    mode TEXT NULL,
    reason TEXT NOT NULL,
    source TEXT NOT NULL,
    version TEXT NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, module_id),
    CONSTRAINT fk_tenant_module_overrides_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_tenant_module_overrides_module
        FOREIGN KEY (module_id)
        REFERENCES public.platform_modules (module_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_tenant_module_overrides_mode
        CHECK (mode IS NULL OR mode ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_tenant_module_overrides_reason
        CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_tenant_module_overrides_source
        CHECK (source IN ('support', 'admin', 'migration', 'package_change', 'bootstrap', 'manual')),
    CONSTRAINT chk_tenant_module_overrides_version
        CHECK (length(btrim(version)) > 0)
);

COMMENT ON TABLE public.tenant_module_overrides IS
    'Tenant-specific module enablement overrides applied after package and template defaults.';

CREATE INDEX IF NOT EXISTS idx_tenant_module_overrides_module_id
    ON public.tenant_module_overrides (module_id);

DROP TRIGGER IF EXISTS trg_tenant_module_overrides_set_updated_at
ON public.tenant_module_overrides;

CREATE TRIGGER trg_tenant_module_overrides_set_updated_at
BEFORE UPDATE ON public.tenant_module_overrides
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.tenant_workflow_overrides (
    tenant_id INTEGER NOT NULL,
    flag_key TEXT NOT NULL,
    flag_value JSONB NOT NULL,
    reason TEXT NOT NULL,
    source TEXT NOT NULL,
    version TEXT NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, flag_key),
    CONSTRAINT fk_tenant_workflow_overrides_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_tenant_workflow_overrides_flag_key
        CHECK (flag_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_tenant_workflow_overrides_flag_value_scalar
        CHECK (jsonb_typeof(flag_value) IN ('string', 'number', 'boolean', 'null')),
    CONSTRAINT chk_tenant_workflow_overrides_reason
        CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_tenant_workflow_overrides_source
        CHECK (source IN ('support', 'admin', 'migration', 'template_default', 'bootstrap', 'manual')),
    CONSTRAINT chk_tenant_workflow_overrides_version
        CHECK (length(btrim(version)) > 0)
);

COMMENT ON TABLE public.tenant_workflow_overrides IS
    'Tenant-specific workflow overrides projected into effective configuration.';

DROP TRIGGER IF EXISTS trg_tenant_workflow_overrides_set_updated_at
ON public.tenant_workflow_overrides;

CREATE TRIGGER trg_tenant_workflow_overrides_set_updated_at
BEFORE UPDATE ON public.tenant_workflow_overrides
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.tenant_role_assignments (
    tenant_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    role_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by_user_id INTEGER NULL,
    source TEXT NOT NULL DEFAULT 'manual',
    PRIMARY KEY (tenant_id, user_id, role_id),
    CONSTRAINT fk_tenant_role_assignments_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_tenant_role_assignments_user
        FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_role_assignments_role
        FOREIGN KEY (role_id)
        REFERENCES public.role_definitions (role_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_role_assignments_assigned_by_user
        FOREIGN KEY (assigned_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_tenant_role_assignments_status
        CHECK (status IN ('active', 'inactive')),
    CONSTRAINT chk_tenant_role_assignments_source
        CHECK (source IN ('manual', 'legacy_migration', 'bootstrap', 'admin', 'support'))
);

COMMENT ON TABLE public.tenant_role_assignments IS
    'Tenant-level role assignments anchored to existing users.id identities.';

CREATE INDEX IF NOT EXISTS idx_tenant_role_assignments_user_id
    ON public.tenant_role_assignments (user_id);

CREATE INDEX IF NOT EXISTS idx_tenant_role_assignments_role_id
    ON public.tenant_role_assignments (role_id);

CREATE TABLE IF NOT EXISTS public.tenant_user_grant_overrides (
    tenant_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    capability_key TEXT NOT NULL,
    grant_mode TEXT NOT NULL DEFAULT 'allow',
    scope_type TEXT NOT NULL,
    reason TEXT NOT NULL,
    source TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, user_id, capability_key, scope_type),
    CONSTRAINT fk_tenant_user_grant_overrides_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_tenant_user_grant_overrides_user
        FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_user_grant_overrides_capability
        FOREIGN KEY (capability_key)
        REFERENCES public.platform_capabilities (capability_key)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_tenant_user_grant_overrides_grant_mode
        CHECK (grant_mode IN ('allow', 'deny')),
    CONSTRAINT chk_tenant_user_grant_overrides_scope_type
        CHECK (scope_type IN ('self', 'branch', 'company', 'global_admin')),
    CONSTRAINT chk_tenant_user_grant_overrides_reason
        CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_tenant_user_grant_overrides_source
        CHECK (source IN ('support', 'manual', 'migration', 'bootstrap', 'role_resolution', 'admin'))
);

COMMENT ON TABLE public.tenant_user_grant_overrides IS
    'User-specific capability overrides inside a tenant.';

CREATE INDEX IF NOT EXISTS idx_tenant_user_grant_overrides_user_id
    ON public.tenant_user_grant_overrides (user_id);

CREATE INDEX IF NOT EXISTS idx_tenant_user_grant_overrides_capability_key
    ON public.tenant_user_grant_overrides (capability_key);

DROP TRIGGER IF EXISTS trg_tenant_user_grant_overrides_set_updated_at
ON public.tenant_user_grant_overrides;

CREATE TRIGGER trg_tenant_user_grant_overrides_set_updated_at
BEFORE UPDATE ON public.tenant_user_grant_overrides
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.onboarding_states (
    tenant_id INTEGER PRIMARY KEY,
    status TEXT NOT NULL,
    current_step TEXT NULL,
    completed_steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_next_action TEXT NULL,
    diagnostics JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_onboarding_states_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_onboarding_states_status
        CHECK (status IN ('not_started', 'in_progress', 'complete', 'blocked', 'failed_recovery_required')),
    CONSTRAINT chk_onboarding_states_current_step
        CHECK (current_step IS NULL OR current_step ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_onboarding_states_completed_steps_array
        CHECK (jsonb_typeof(completed_steps) = 'array'),
    CONSTRAINT chk_onboarding_states_required_next_action
        CHECK (required_next_action IS NULL OR required_next_action ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_onboarding_states_diagnostics_object
        CHECK (jsonb_typeof(diagnostics) = 'object')
);

COMMENT ON TABLE public.onboarding_states IS
    'Explicit onboarding and provisioning state per tenant.';

CREATE INDEX IF NOT EXISTS idx_onboarding_states_status
    ON public.onboarding_states (status);

DROP TRIGGER IF EXISTS trg_onboarding_states_set_updated_at
ON public.onboarding_states;

CREATE TRIGGER trg_onboarding_states_set_updated_at
BEFORE UPDATE ON public.onboarding_states
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();
