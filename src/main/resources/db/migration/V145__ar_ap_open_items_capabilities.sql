-- =====================================================================
-- V145: Capabilities and role grants for AR/AP open items, credit control,
-- client account statements, and credit/debit notes.
--
-- Spec: OPEN_ITEMS_REVISED_SCHEMA_PLAN.md §7 · Review blocker B8.
--
-- IMPORTANT: uses NEW capability keys only. The existing key
-- 'clients.statement.view' (V134) keeps its trade-in seller-statement
-- meaning and is deliberately NOT touched here — re-describing it would
-- silently widen what its existing Owner/BranchManager/Accountant grants
-- expose. Full client ACCOUNT statements use 'clients.account.statement.view'.
--
-- Grant policy (per review F11 — capability scope is company-wide and
-- branch isolation is membership-based, so write capabilities stay with
-- financial roles until server-side branch filtering is proven by tests):
--   Owner         -> everything
--   Accountant    -> everything financial (view + allocate + notes + credit view)
--   BranchManager -> view-only
-- =====================================================================

INSERT INTO public.platform_capabilities (
    capability_key, module_id, resource, action, scope_type, status, description
) VALUES
    ('clients.account.statement.view', 'clients',   'account_statement', 'view',     'company', 'active', 'View full client account statements and receivable aging.'),
    ('clients.credit.view',            'clients',   'credit',            'view',     'company', 'active', 'View client credit limit, terms, status and current exposure.'),
    ('clients.credit.manage',          'clients',   'credit',            'manage',   'company', 'active', 'Set client credit limit, payment terms and credit status.'),
    ('clients.openitems.view',         'clients',   'openitems',         'view',     'company', 'active', 'View client open items (unpaid receivable documents).'),
    ('clients.openitems.allocate',     'clients',   'openitems',         'allocate', 'company', 'active', 'Allocate client receipts and credit notes to open items.'),
    ('clients.creditnote.create',      'clients',   'creditnote',        'create',   'company', 'active', 'Issue a client credit note.'),
    ('clients.creditnote.reverse',     'clients',   'creditnote',        'reverse',  'company', 'active', 'Reverse a client credit note.'),
    ('suppliers.openitems.view',       'suppliers', 'openitems',         'view',     'company', 'active', 'View supplier open items (unpaid payable documents).'),
    ('suppliers.openitems.allocate',   'suppliers', 'openitems',         'allocate', 'company', 'active', 'Allocate supplier payments and debit notes to open items.'),
    ('suppliers.debitnote.create',     'suppliers', 'debitnote',         'create',   'company', 'active', 'Issue a supplier debit note.'),
    ('suppliers.debitnote.reverse',    'suppliers', 'debitnote',         'reverse',  'company', 'active', 'Reverse a supplier debit note.')
ON CONFLICT (capability_key) DO UPDATE
SET module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

-- Owner: everything
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Owner', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.account.statement.view',
    'clients.credit.view', 'clients.credit.manage',
    'clients.openitems.view', 'clients.openitems.allocate',
    'clients.creditnote.create', 'clients.creditnote.reverse',
    'suppliers.openitems.view', 'suppliers.openitems.allocate',
    'suppliers.debitnote.create', 'suppliers.debitnote.reverse'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode, grant_version = EXCLUDED.grant_version;

-- Accountant: financial subset (no credit.manage — limit changes are an
-- owner-level commercial decision; adjust later if the business disagrees)
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'Accountant', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.account.statement.view',
    'clients.credit.view',
    'clients.openitems.view', 'clients.openitems.allocate',
    'clients.creditnote.create', 'clients.creditnote.reverse',
    'suppliers.openitems.view', 'suppliers.openitems.allocate',
    'suppliers.debitnote.create', 'suppliers.debitnote.reverse'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode, grant_version = EXCLUDED.grant_version;

-- BranchManager: view-only
INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
SELECT 'BranchManager', capability_key, scope_type, 'allow', 'v1'
FROM public.platform_capabilities
WHERE capability_key IN (
    'clients.account.statement.view',
    'clients.credit.view',
    'clients.openitems.view',
    'suppliers.openitems.view'
)
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode, grant_version = EXCLUDED.grant_version;
