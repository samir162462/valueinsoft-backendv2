-- V66__supplier_capabilities_expansion.sql
-- Add granular supplier capabilities and default role grants

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('module.suppliers.access', 'suppliers', 'module', 'access', 'company', 'active', 'Access the suppliers module.'),
    ('suppliers.list.view', 'suppliers', 'list', 'view', 'company', 'active', 'View the list of suppliers.'),
    ('suppliers.account.create', 'suppliers', 'account', 'create', 'company', 'active', 'Create a new supplier.'),
    ('suppliers.account.edit', 'suppliers', 'account', 'edit', 'company', 'active', 'Edit an existing supplier.'),
    ('suppliers.account.archive', 'suppliers', 'account', 'archive', 'company', 'active', 'Archive a supplier.'),
    ('suppliers.account.delete', 'suppliers', 'account', 'delete', 'company', 'active', 'Delete a supplier.'),
    ('suppliers.payment.create', 'suppliers', 'payment', 'create', 'company', 'active', 'Create a supplier payment.'),
    ('suppliers.payment.reverse', 'suppliers', 'payment', 'reverse', 'company', 'active', 'Reverse a supplier payment.'),
    ('suppliers.statement.view', 'suppliers', 'statement', 'view', 'company', 'active', 'View supplier statements.'),
    ('suppliers.return.create', 'suppliers', 'return', 'create', 'company', 'active', 'Create a supplier return.'),
    ('suppliers.return.reverse', 'suppliers', 'return', 'reverse', 'company', 'active', 'Reverse a supplier return.')
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

-- Grants for Owner (All)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Owner', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE module_id = 'suppliers'
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

-- Grants for BranchManager (Operational subset)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'BranchManager', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'module.suppliers.access',
    'suppliers.list.view',
    'suppliers.account.create',
    'suppliers.account.edit',
    'suppliers.statement.view'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

-- Grants for Accountant (Financial subset)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Accountant', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'module.suppliers.access',
    'suppliers.list.view',
    'suppliers.payment.create',
    'suppliers.payment.reverse',
    'suppliers.statement.view',
    'suppliers.return.create',
    'suppliers.return.reverse'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

-- Grants for InventoryClerk (Inventory subset)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'InventoryClerk', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'module.suppliers.access',
    'suppliers.list.view',
    'suppliers.account.create',
    'suppliers.account.edit',
    'suppliers.return.create'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
