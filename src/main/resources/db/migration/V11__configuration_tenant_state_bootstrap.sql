INSERT INTO public.company_templates (
    template_id,
    display_name,
    business_type,
    status,
    config_version,
    description
) VALUES
    (
        'general_business',
        'General Business',
        'general',
        'active',
        'v1',
        'Generic template for legacy or mixed-business tenants during configuration migration.'
    )
ON CONFLICT (template_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    business_type = EXCLUDED.business_type,
    status = EXCLUDED.status,
    config_version = EXCLUDED.config_version,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.company_template_module_defaults (
    template_id,
    module_id,
    enabled,
    recommended,
    mode,
    notes
) VALUES
    ('general_business', 'dashboard', TRUE, TRUE, 'standard', 'Dashboard should be available for legacy tenants.'),
    ('general_business', 'pos', TRUE, FALSE, 'standard', 'POS remains available for businesses already operating sales flows.'),
    ('general_business', 'inventory', TRUE, FALSE, 'full', 'Inventory stays available to avoid restricting existing operations.'),
    ('general_business', 'clients', TRUE, TRUE, 'standard', 'Client records are broadly useful across existing tenants.'),
    ('general_business', 'suppliers', TRUE, FALSE, 'standard', 'Supplier workflows remain available for inventory-driven tenants.'),
    ('general_business', 'finance', TRUE, TRUE, 'standard', 'Finance visibility should remain available during migration.'),
    ('general_business', 'users', TRUE, TRUE, 'advanced', 'User administration remains available during access migration.'),
    ('general_business', 'company_settings', TRUE, TRUE, 'basic', 'Company settings are required for tenant administration.'),
    ('general_business', 'profile', TRUE, TRUE, 'standard', 'Profile access must always remain available.'),
    ('general_business', 'onboarding', TRUE, TRUE, 'guided', 'Onboarding state remains explicit even for migrated tenants.')
ON CONFLICT (template_id, module_id) DO UPDATE
SET
    enabled = EXCLUDED.enabled,
    recommended = EXCLUDED.recommended,
    mode = EXCLUDED.mode,
    notes = EXCLUDED.notes,
    updated_at = NOW();

INSERT INTO public.company_template_workflow_defaults (
    template_id,
    flag_key,
    flag_value,
    notes
) VALUES
    ('general_business', 'branch_active_required', 'true'::jsonb, 'Legacy tenants usually operate through branch context.'),
    ('general_business', 'shift_tracking_enabled', 'false'::jsonb, 'Shift tracking should not be forced globally during bootstrap.')
ON CONFLICT (template_id, flag_key) DO UPDATE
SET
    flag_value = EXCLUDED.flag_value,
    notes = EXCLUDED.notes,
    updated_at = NOW();

WITH branch_counts AS (
    SELECT
        b."companyId" AS company_id,
        COUNT(*)::INTEGER AS branch_count
    FROM public."Branch" b
    GROUP BY b."companyId"
),
legacy_companies AS (
    SELECT
        c.id AS company_id,
        c."planName" AS legacy_plan_name,
        c."establishedTime" AS established_time,
        COALESCE(bc.branch_count, 0) AS branch_count,
        lower(regexp_replace(COALESCE(c."planName", 'enterprise'), '[^a-z0-9]+', '_', 'g')) AS normalized_plan_name
    FROM public."Company" c
    LEFT JOIN branch_counts bc
        ON bc.company_id = c.id
)
INSERT INTO public.tenants (
    tenant_id,
    package_id,
    template_id,
    status,
    config_version,
    legacy_plan_name,
    bootstrap_source,
    created_at,
    updated_at
)
SELECT
    lc.company_id AS tenant_id,
    CASE
        WHEN lc.normalized_plan_name IN ('starter', 'pro', 'enterprise') THEN lc.normalized_plan_name
        WHEN lc.normalized_plan_name IN ('basic', 'free') THEN 'starter'
        WHEN lc.normalized_plan_name IN ('premium', 'business', 'professional') THEN 'pro'
        ELSE 'enterprise'
    END AS package_id,
    'general_business' AS template_id,
    CASE
        WHEN lc.branch_count > 0 THEN 'active'
        ELSE 'onboarding'
    END AS status,
    'v1' AS config_version,
    lc.legacy_plan_name,
    'legacy_migration' AS bootstrap_source,
    COALESCE(NOW(), NOW()) AS created_at,
    NOW() AS updated_at
FROM legacy_companies lc
ON CONFLICT (tenant_id) DO NOTHING;

WITH tenant_branch_counts AS (
    SELECT
        t.tenant_id,
        COUNT(b."branchId")::INTEGER AS branch_count
    FROM public.tenants t
    LEFT JOIN public."Branch" b
        ON b."companyId" = t.tenant_id
    GROUP BY t.tenant_id
)
INSERT INTO public.onboarding_states (
    tenant_id,
    status,
    current_step,
    completed_steps,
    required_next_action,
    diagnostics,
    created_at,
    updated_at
)
SELECT
    tbc.tenant_id,
    CASE
        WHEN tbc.branch_count > 0 THEN 'complete'
        ELSE 'in_progress'
    END AS status,
    CASE
        WHEN tbc.branch_count > 0 THEN NULL
        ELSE 'branch_setup'
    END AS current_step,
    CASE
        WHEN tbc.branch_count > 0 THEN '["company_created","branch_created","runtime_initialized"]'::jsonb
        ELSE '["company_created"]'::jsonb
    END AS completed_steps,
    CASE
        WHEN tbc.branch_count > 0 THEN NULL
        ELSE 'create_first_branch'
    END AS required_next_action,
    '{}'::jsonb AS diagnostics,
    NOW() AS created_at,
    NOW() AS updated_at
FROM tenant_branch_counts tbc
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO public.tenant_role_assignments (
    tenant_id,
    user_id,
    role_id,
    status,
    assigned_at,
    assigned_by_user_id,
    source
)
SELECT
    c.id AS tenant_id,
    c."ownerId" AS user_id,
    'Owner' AS role_id,
    'active' AS status,
    NOW() AS assigned_at,
    NULL AS assigned_by_user_id,
    'legacy_migration' AS source
FROM public."Company" c
JOIN public.tenants t
    ON t.tenant_id = c.id
JOIN public.users u
    ON u.id = c."ownerId"
ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;
