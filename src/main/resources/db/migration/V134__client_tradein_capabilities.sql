-- V134__client_tradein_capabilities.sql
-- Capabilities and default role grants for the client trade-in feature
-- (buying inventory from existing clients). Mirrors V66 supplier pattern.

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('clients.tradein.view',    'clients', 'tradein',  'view',   'company', 'active', 'View products a client sold to the shop and the related payable balance.'),
    ('clients.tradein.create',  'clients', 'tradein',  'create', 'company', 'active', 'Receive inventory purchased from a client (customer trade-in).'),
    ('clients.payment.create',  'clients', 'payment',  'create', 'company', 'active', 'Pay a client for products purchased from them.'),
    ('clients.statement.view',  'clients', 'statement','view',   'company', 'active', 'View a client seller statement (trade-in receipts, payments, balance).'),
    ('clients.account.archive', 'clients', 'account',  'archive','company', 'active', 'Archive or restore a client account (no hard delete).')
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

-- Owner: full access
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Owner', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.tradein.view',
    'clients.tradein.create',
    'clients.payment.create',
    'clients.statement.view',
    'clients.account.archive'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

-- BranchManager: operational subset (receive + view; payments included since
-- branch managers settle supplier payables today via suppliers.account.edit)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'BranchManager', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.tradein.view',
    'clients.tradein.create',
    'clients.payment.create',
    'clients.statement.view'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

-- Accountant: financial subset (view, pay, statement — no receiving)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Accountant', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.tradein.view',
    'clients.payment.create',
    'clients.statement.view'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
