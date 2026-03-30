CREATE OR REPLACE FUNCTION valueinsoft_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS public.platform_modules (
    module_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    category TEXT NOT NULL,
    status TEXT NOT NULL,
    default_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_version TEXT NOT NULL DEFAULT 'v1',
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_platform_modules_module_id
        CHECK (module_id ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_platform_modules_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_platform_modules_category
        CHECK (category ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_platform_modules_status
        CHECK (status IN ('active', 'experimental', 'deprecated', 'retired')),
    CONSTRAINT chk_platform_modules_config_version
        CHECK (length(btrim(config_version)) > 0),
    CONSTRAINT chk_platform_modules_description
        CHECK (length(btrim(description)) > 0)
);

COMMENT ON TABLE public.platform_modules IS
    'Platform-level module definitions used by configuration-driven enablement and navigation.';

DROP TRIGGER IF EXISTS trg_platform_modules_set_updated_at
ON public.platform_modules;

CREATE TRIGGER trg_platform_modules_set_updated_at
BEFORE UPDATE ON public.platform_modules
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.platform_capabilities (
    capability_key TEXT PRIMARY KEY,
    module_id TEXT NOT NULL,
    resource TEXT NOT NULL,
    action TEXT NOT NULL,
    scope_type TEXT NOT NULL,
    status TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_capabilities_module
        FOREIGN KEY (module_id)
        REFERENCES public.platform_modules (module_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT uq_platform_capabilities_definition
        UNIQUE (module_id, resource, action, scope_type),
    CONSTRAINT chk_platform_capabilities_key
        CHECK (capability_key ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}$'),
    CONSTRAINT chk_platform_capabilities_resource
        CHECK (resource ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_platform_capabilities_action
        CHECK (action ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_platform_capabilities_scope_type
        CHECK (scope_type IN ('self', 'branch', 'company', 'global_admin')),
    CONSTRAINT chk_platform_capabilities_status
        CHECK (status IN ('active', 'deprecated')),
    CONSTRAINT chk_platform_capabilities_description
        CHECK (length(btrim(description)) > 0)
);

COMMENT ON TABLE public.platform_capabilities IS
    'Stable capability dictionary used by backend authorization and frontend capability consumption.';

CREATE INDEX IF NOT EXISTS idx_platform_capabilities_module_id
    ON public.platform_capabilities (module_id);

CREATE INDEX IF NOT EXISTS idx_platform_capabilities_scope_type
    ON public.platform_capabilities (scope_type);

DROP TRIGGER IF EXISTS trg_platform_capabilities_set_updated_at
ON public.platform_capabilities;

CREATE TRIGGER trg_platform_capabilities_set_updated_at
BEFORE UPDATE ON public.platform_capabilities
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.role_definitions (
    role_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    role_type TEXT NOT NULL,
    status TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_role_definitions_role_id
        CHECK (role_id ~ '^[A-Za-z][A-Za-z0-9_]*$'),
    CONSTRAINT chk_role_definitions_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_role_definitions_role_type
        CHECK (role_type IN ('platform', 'tenant', 'bootstrap')),
    CONSTRAINT chk_role_definitions_status
        CHECK (status IN ('active', 'deprecated')),
    CONSTRAINT chk_role_definitions_description
        CHECK (length(btrim(description)) > 0)
);

COMMENT ON TABLE public.role_definitions IS
    'Platform and tenant role definitions used by grant resolution.';

DROP TRIGGER IF EXISTS trg_role_definitions_set_updated_at
ON public.role_definitions;

CREATE TRIGGER trg_role_definitions_set_updated_at
BEFORE UPDATE ON public.role_definitions
FOR EACH ROW
EXECUTE FUNCTION valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.role_grants (
    role_id TEXT NOT NULL,
    capability_key TEXT NOT NULL,
    scope_type TEXT NOT NULL,
    grant_mode TEXT NOT NULL DEFAULT 'allow',
    grant_version TEXT NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, capability_key, scope_type),
    CONSTRAINT fk_role_grants_role
        FOREIGN KEY (role_id)
        REFERENCES public.role_definitions (role_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_role_grants_capability
        FOREIGN KEY (capability_key)
        REFERENCES public.platform_capabilities (capability_key)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_role_grants_scope_type
        CHECK (scope_type IN ('self', 'branch', 'company', 'global_admin')),
    CONSTRAINT chk_role_grants_grant_mode
        CHECK (grant_mode IN ('allow', 'deny')),
    CONSTRAINT chk_role_grants_grant_version
        CHECK (length(btrim(grant_version)) > 0)
);

COMMENT ON TABLE public.role_grants IS
    'Role-to-capability grants. Deny mode is reserved for future use.';

CREATE INDEX IF NOT EXISTS idx_role_grants_capability_key
    ON public.role_grants (capability_key);
