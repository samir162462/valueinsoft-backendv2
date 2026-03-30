INSERT INTO public.platform_modules (
    module_id,
    display_name,
    category,
    status,
    default_enabled,
    config_version,
    description
) VALUES
    ('dashboard', 'Dashboard', 'core', 'active', TRUE, 'v1', 'Primary operational overview.'),
    ('pos', 'Point Of Sale', 'operations', 'active', FALSE, 'v1', 'Sales and checkout workflows.'),
    ('inventory', 'Inventory', 'inventory', 'active', FALSE, 'v1', 'Item catalog, stock movement, and counts.'),
    ('clients', 'Clients', 'crm', 'active', TRUE, 'v1', 'Customer and client management.'),
    ('suppliers', 'Suppliers', 'inventory', 'active', FALSE, 'v1', 'Supplier directory and procurement support.'),
    ('finance', 'Finance', 'finance', 'active', FALSE, 'v1', 'Income, expenses, and reporting.'),
    ('users', 'Users', 'admin', 'active', TRUE, 'v1', 'User and role administration.'),
    ('company_settings', 'Company Settings', 'admin', 'active', TRUE, 'v1', 'Tenant settings and business preferences.'),
    ('profile', 'Profile', 'core', 'active', TRUE, 'v1', 'Self-account management.'),
    ('onboarding', 'Onboarding', 'core', 'active', TRUE, 'v1', 'Company setup and provisioning journey.'),
    ('web_admin', 'Web Admin', 'platform_admin', 'experimental', FALSE, 'v1', 'Platform-level administrative tooling.')
ON CONFLICT (module_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    category = EXCLUDED.category,
    status = EXCLUDED.status,
    default_enabled = EXCLUDED.default_enabled,
    config_version = EXCLUDED.config_version,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('dashboard.home.view', 'dashboard', 'home', 'view', 'company', 'active', 'View dashboard home.'),
    ('pos.sale.create', 'pos', 'sale', 'create', 'branch', 'active', 'Create sales in the active branch.'),
    ('inventory.item.read', 'inventory', 'item', 'read', 'branch', 'active', 'Read inventory items in branch scope.'),
    ('inventory.item.create', 'inventory', 'item', 'create', 'branch', 'active', 'Create inventory items in branch scope.'),
    ('inventory.adjustment.create', 'inventory', 'adjustment', 'create', 'branch', 'active', 'Create stock adjustments.'),
    ('clients.account.read', 'clients', 'account', 'read', 'company', 'active', 'Read client accounts.'),
    ('clients.account.create', 'clients', 'account', 'create', 'company', 'active', 'Create client accounts.'),
    ('suppliers.account.read', 'suppliers', 'account', 'read', 'company', 'active', 'Read supplier accounts.'),
    ('finance.entry.read', 'finance', 'entry', 'read', 'company', 'active', 'Read finance entries.'),
    ('finance.report.read', 'finance', 'report', 'read', 'company', 'active', 'View finance reports.'),
    ('users.account.read', 'users', 'account', 'read', 'company', 'active', 'Read user accounts.'),
    ('users.account.create', 'users', 'account', 'create', 'company', 'active', 'Create user accounts.'),
    ('users.account.edit', 'users', 'account', 'edit', 'company', 'active', 'Edit user accounts.'),
    ('company.settings.read', 'company_settings', 'settings', 'read', 'company', 'active', 'Read company settings.'),
    ('company.settings.edit', 'company_settings', 'settings', 'edit', 'company', 'active', 'Edit company settings.'),
    ('profile.self.read', 'profile', 'self', 'read', 'self', 'active', 'Read own profile.'),
    ('profile.self.edit', 'profile', 'self', 'edit', 'self', 'active', 'Edit own profile.'),
    ('tenant.setup.manage', 'onboarding', 'setup', 'manage', 'company', 'active', 'Drive tenant onboarding and setup.'),
    ('platform.admin.read', 'web_admin', 'admin', 'read', 'global_admin', 'active', 'Access platform admin features.')
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_definitions (
    role_id,
    display_name,
    role_type,
    status,
    description
) VALUES
    ('Owner', 'Owner', 'platform', 'active', 'Top tenant-level operator.'),
    ('BranchManager', 'Branch Manager', 'platform', 'active', 'Branch-level operational manager.'),
    ('Cashier', 'Cashier', 'platform', 'active', 'Sales execution role.'),
    ('InventoryClerk', 'Inventory Clerk', 'platform', 'active', 'Inventory operations role.'),
    ('Accountant', 'Accountant', 'platform', 'active', 'Finance and reporting role.'),
    ('SupportAdmin', 'Support Admin', 'platform', 'active', 'Platform support and inspection role.')
ON CONFLICT (role_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    role_type = EXCLUDED.role_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (
    role_id,
    capability_key,
    scope_type,
    grant_mode,
    grant_version
) VALUES
    ('Owner', 'dashboard.home.view', 'company', 'allow', 'v1'),
    ('Owner', 'pos.sale.create', 'branch', 'allow', 'v1'),
    ('Owner', 'inventory.item.read', 'company', 'allow', 'v1'),
    ('Owner', 'inventory.item.create', 'company', 'allow', 'v1'),
    ('Owner', 'inventory.adjustment.create', 'company', 'allow', 'v1'),
    ('Owner', 'clients.account.read', 'company', 'allow', 'v1'),
    ('Owner', 'clients.account.create', 'company', 'allow', 'v1'),
    ('Owner', 'suppliers.account.read', 'company', 'allow', 'v1'),
    ('Owner', 'finance.entry.read', 'company', 'allow', 'v1'),
    ('Owner', 'finance.report.read', 'company', 'allow', 'v1'),
    ('Owner', 'users.account.read', 'company', 'allow', 'v1'),
    ('Owner', 'users.account.create', 'company', 'allow', 'v1'),
    ('Owner', 'users.account.edit', 'company', 'allow', 'v1'),
    ('Owner', 'company.settings.read', 'company', 'allow', 'v1'),
    ('Owner', 'company.settings.edit', 'company', 'allow', 'v1'),
    ('Owner', 'profile.self.read', 'self', 'allow', 'v1'),
    ('Owner', 'profile.self.edit', 'self', 'allow', 'v1'),
    ('Owner', 'tenant.setup.manage', 'company', 'allow', 'v1'),
    ('BranchManager', 'dashboard.home.view', 'branch', 'allow', 'v1'),
    ('BranchManager', 'pos.sale.create', 'branch', 'allow', 'v1'),
    ('BranchManager', 'inventory.item.read', 'branch', 'allow', 'v1'),
    ('BranchManager', 'inventory.adjustment.create', 'branch', 'allow', 'v1'),
    ('BranchManager', 'clients.account.read', 'company', 'allow', 'v1'),
    ('Cashier', 'pos.sale.create', 'branch', 'allow', 'v1'),
    ('Cashier', 'profile.self.read', 'self', 'allow', 'v1'),
    ('Cashier', 'profile.self.edit', 'self', 'allow', 'v1'),
    ('InventoryClerk', 'inventory.item.read', 'branch', 'allow', 'v1'),
    ('InventoryClerk', 'inventory.item.create', 'branch', 'allow', 'v1'),
    ('InventoryClerk', 'inventory.adjustment.create', 'branch', 'allow', 'v1'),
    ('Accountant', 'finance.entry.read', 'company', 'allow', 'v1'),
    ('Accountant', 'finance.report.read', 'company', 'allow', 'v1'),
    ('SupportAdmin', 'platform.admin.read', 'global_admin', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
