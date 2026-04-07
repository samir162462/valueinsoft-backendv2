INSERT INTO public.package_module_policies (
    package_id,
    module_id,
    enabled,
    mode,
    limits,
    policy_version
)
WITH all_features(module_id) AS (
    VALUES
        ('pos_checkout'),
        ('pos_returns'),
        ('inventory_catalog'),
        ('inventory_item_management'),
        ('inventory_transactions'),
        ('inventory_bounced_back'),
        ('inventory_barcode'),
        ('inventory_scanner'),
        ('inventory_categories'),
        ('suppliers_management'),
        ('clients_management'),
        ('client_vouchers'),
        ('finance_sales_reports'),
        ('finance_expenses'),
        ('company_billing_settings'),
        ('user_management')
),
starter_features(module_id) AS (
    VALUES
        ('pos_checkout'),
        ('inventory_catalog'),
        ('inventory_item_management'),
        ('inventory_categories'),
        ('clients_management'),
        ('finance_sales_reports'),
        ('company_billing_settings'),
        ('user_management')
),
plan_defaults(package_id, mode, max_users) AS (
    VALUES
        ('starter', 'basic', 5),
        ('pro', 'standard', 40),
        ('enterprise', 'advanced', 500)
)
SELECT
    pd.package_id,
    af.module_id,
    CASE
        WHEN pd.package_id = 'starter' THEN af.module_id IN (SELECT module_id FROM starter_features)
        ELSE TRUE
    END AS enabled,
    CASE
        WHEN pd.package_id = 'starter' AND af.module_id NOT IN (SELECT module_id FROM starter_features) THEN NULL
        WHEN af.module_id = 'user_management' AND pd.package_id <> 'starter' THEN 'advanced'
        ELSE pd.mode
    END AS mode,
    CASE
        WHEN af.module_id = 'user_management' THEN jsonb_build_object('max_users', pd.max_users)
        ELSE '{}'::jsonb
    END AS limits,
    'v1' AS policy_version
FROM plan_defaults pd
CROSS JOIN all_features af
ON CONFLICT (package_id, module_id) DO UPDATE
SET
    enabled = EXCLUDED.enabled,
    mode = EXCLUDED.mode,
    limits = EXCLUDED.limits,
    policy_version = EXCLUDED.policy_version,
    updated_at = NOW();
