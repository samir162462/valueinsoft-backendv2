-- Company administrators can replace the default capability-derived audience for any
-- notification type with an explicit set of users and/or tenant roles.

CREATE TABLE IF NOT EXISTS public.notification_company_route (
    company_id INTEGER NOT NULL,
    type_key TEXT NOT NULL,
    updated_by_user_id INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (company_id, type_key),
    CONSTRAINT fk_notification_company_route_company
        FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_notification_company_route_type
        FOREIGN KEY (type_key)
        REFERENCES public.notification_type_catalog (type_key)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_notification_company_route_updated_by
        FOREIGN KEY (updated_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS public.notification_company_route_target (
    target_id BIGSERIAL PRIMARY KEY,
    company_id INTEGER NOT NULL,
    type_key TEXT NOT NULL,
    target_kind TEXT NOT NULL,
    user_id INTEGER,
    role_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_company_route_target_route
        FOREIGN KEY (company_id, type_key)
        REFERENCES public.notification_company_route (company_id, type_key)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_notification_company_route_target_user
        FOREIGN KEY (user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_notification_company_route_target_role
        FOREIGN KEY (role_id)
        REFERENCES public.role_definitions (role_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_notification_company_route_target_kind
        CHECK (target_kind IN ('user', 'role')),
    CONSTRAINT chk_notification_company_route_target_reference
        CHECK (
            (target_kind = 'user' AND user_id IS NOT NULL AND role_id IS NULL)
            OR
            (target_kind = 'role' AND role_id IS NOT NULL AND user_id IS NULL)
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_company_route_target_user
    ON public.notification_company_route_target (company_id, type_key, user_id)
    WHERE target_kind = 'user';

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_company_route_target_role
    ON public.notification_company_route_target (company_id, type_key, role_id)
    WHERE target_kind = 'role';

CREATE INDEX IF NOT EXISTS idx_notification_company_route_target_user_lookup
    ON public.notification_company_route_target (company_id, type_key, user_id);

CREATE INDEX IF NOT EXISTS idx_notification_company_route_target_role_lookup
    ON public.notification_company_route_target (company_id, type_key, role_id);

DROP TRIGGER IF EXISTS trg_notification_company_route_set_updated_at
ON public.notification_company_route;

CREATE TRIGGER trg_notification_company_route_set_updated_at
BEFORE UPDATE ON public.notification_company_route
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES (
    'notification.routing.manage.company',
    'web_admin',
    'notification_routing',
    'manage',
    'company',
    'active',
    'Configure which company users and roles receive each notification event type.'
)
ON CONFLICT (capability_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (
    role_id, capability_key, scope_type, grant_mode, grant_version
)
SELECT role_id, 'notification.routing.manage.company', 'company', 'allow', 'v1'
FROM public.role_definitions
WHERE role_id IN ('Owner', 'Admin')
  AND status = 'active'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

COMMENT ON TABLE public.notification_company_route IS
    'Presence of a row means the company explicitly overrides the default capability audience for the notification type.';

COMMENT ON TABLE public.notification_company_route_target IS
    'Explicit user and role recipients for a company notification route; an explicit route may intentionally have no targets.';
