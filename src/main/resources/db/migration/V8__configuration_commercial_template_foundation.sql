CREATE TABLE IF NOT EXISTS public.package_plans (
    package_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL,
    price_code TEXT NOT NULL,
    config_version TEXT NOT NULL DEFAULT 'v1',
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_package_plans_package_id
        CHECK (package_id ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_package_plans_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_package_plans_status
        CHECK (status IN ('active', 'retired')),
    CONSTRAINT chk_package_plans_price_code
        CHECK (length(btrim(price_code)) > 0),
    CONSTRAINT chk_package_plans_config_version
        CHECK (length(btrim(config_version)) > 0),
    CONSTRAINT chk_package_plans_description
        CHECK (length(btrim(description)) > 0)
);

COMMENT ON TABLE public.package_plans IS
    'Commercial package definitions that control default module availability and limits.';

DROP TRIGGER IF EXISTS trg_package_plans_set_updated_at
ON public.package_plans;

CREATE TRIGGER trg_package_plans_set_updated_at
BEFORE UPDATE ON public.package_plans
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.package_module_policies (
    package_id TEXT NOT NULL,
    module_id TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    mode TEXT NULL,
    limits JSONB NOT NULL DEFAULT '{}'::jsonb,
    policy_version TEXT NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (package_id, module_id),
    CONSTRAINT fk_package_module_policies_package
        FOREIGN KEY (package_id)
        REFERENCES public.package_plans (package_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_package_module_policies_module
        FOREIGN KEY (module_id)
        REFERENCES public.platform_modules (module_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_package_module_policies_mode
        CHECK (mode IS NULL OR mode ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_package_module_policies_limits_object
        CHECK (jsonb_typeof(limits) = 'object'),
    CONSTRAINT chk_package_module_policies_policy_version
        CHECK (length(btrim(policy_version)) > 0)
);

COMMENT ON TABLE public.package_module_policies IS
    'Per-package module enablement defaults, modes, and commercial limits.';

CREATE INDEX IF NOT EXISTS idx_package_module_policies_module_id
    ON public.package_module_policies (module_id);

DROP TRIGGER IF EXISTS trg_package_module_policies_set_updated_at
ON public.package_module_policies;

CREATE TRIGGER trg_package_module_policies_set_updated_at
BEFORE UPDATE ON public.package_module_policies
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.company_templates (
    template_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    business_type TEXT NOT NULL,
    status TEXT NOT NULL,
    config_version TEXT NOT NULL DEFAULT 'v1',
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_company_templates_template_id
        CHECK (template_id ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_company_templates_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_company_templates_business_type
        CHECK (business_type ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_company_templates_status
        CHECK (status IN ('active', 'experimental', 'retired')),
    CONSTRAINT chk_company_templates_config_version
        CHECK (length(btrim(config_version)) > 0),
    CONSTRAINT chk_company_templates_description
        CHECK (length(btrim(description)) > 0)
);

COMMENT ON TABLE public.company_templates IS
    'Operational company-building templates used during tenant setup and module defaults.';

DROP TRIGGER IF EXISTS trg_company_templates_set_updated_at
ON public.company_templates;

CREATE TRIGGER trg_company_templates_set_updated_at
BEFORE UPDATE ON public.company_templates
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.company_template_module_defaults (
    template_id TEXT NOT NULL,
    module_id TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    mode TEXT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (template_id, module_id),
    CONSTRAINT fk_company_template_module_defaults_template
        FOREIGN KEY (template_id)
        REFERENCES public.company_templates (template_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_company_template_module_defaults_module
        FOREIGN KEY (module_id)
        REFERENCES public.platform_modules (module_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_company_template_module_defaults_mode
        CHECK (mode IS NULL OR mode ~ '^[a-z][a-z0-9_]*$')
);

COMMENT ON TABLE public.company_template_module_defaults IS
    'Template-driven default module states and operating modes.';

CREATE INDEX IF NOT EXISTS idx_company_template_module_defaults_module_id
    ON public.company_template_module_defaults (module_id);

DROP TRIGGER IF EXISTS trg_company_template_module_defaults_set_updated_at
ON public.company_template_module_defaults;

CREATE TRIGGER trg_company_template_module_defaults_set_updated_at
BEFORE UPDATE ON public.company_template_module_defaults
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.company_template_workflow_defaults (
    template_id TEXT NOT NULL,
    flag_key TEXT NOT NULL,
    flag_value JSONB NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (template_id, flag_key),
    CONSTRAINT fk_company_template_workflow_defaults_template
        FOREIGN KEY (template_id)
        REFERENCES public.company_templates (template_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_company_template_workflow_defaults_flag_key
        CHECK (flag_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_company_template_workflow_defaults_flag_value_scalar
        CHECK (jsonb_typeof(flag_value) IN ('string', 'number', 'boolean', 'null'))
);

COMMENT ON TABLE public.company_template_workflow_defaults IS
    'Template-driven workflow flags projected into effective configuration.';

DROP TRIGGER IF EXISTS trg_company_template_workflow_defaults_set_updated_at
ON public.company_template_workflow_defaults;

CREATE TRIGGER trg_company_template_workflow_defaults_set_updated_at
BEFORE UPDATE ON public.company_template_workflow_defaults
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();
