INSERT INTO public.platform_modules (module_id, display_name, category, status, description)
VALUES ('whatsapp', 'WhatsApp Integration', 'integration', 'active', 'WhatsApp messaging integration for the platform')
ON CONFLICT (module_id) DO NOTHING;

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('whatsapp.settings.view',   'whatsapp', 'settings', 'view',   'company', 'active', 'View WhatsApp settings and status.'),
    ('whatsapp.settings.manage', 'whatsapp', 'settings', 'manage', 'company', 'active', 'Manage WhatsApp settings and access token.'),
    ('whatsapp.send.test',       'whatsapp', 'send',     'test',   'company', 'active', 'Send test WhatsApp messages.'),
    ('whatsapp.logs.view',       'whatsapp', 'logs',     'view',   'company', 'active', 'View WhatsApp message logs.')
ON CONFLICT (capability_key) DO UPDATE
SET module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
VALUES
    ('Owner', 'whatsapp.settings.view',   'company', 'allow', 'v1'),
    ('Owner', 'whatsapp.settings.manage', 'company', 'allow', 'v1'),
    ('Owner', 'whatsapp.send.test',       'company', 'allow', 'v1'),
    ('Owner', 'whatsapp.logs.view',       'company', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
