INSERT INTO public.branch_setting_definitions (
    group_key,
    setting_key,
    display_name,
    description,
    value_type,
    field_type,
    default_value_json,
    options_json,
    validation_json,
    active,
    sort_order
)
VALUES (
    'pos',
    'pos.hideOutOfStockDefault',
    'Hide out-of-stock products by default',
    'Controls the default state of the POS "in-stock only" toggle. When enabled, out-of-stock products are hidden in the point of sale until the cashier turns the toggle off.',
    'boolean',
    'switch',
    'false'::jsonb,
    '[]'::jsonb,
    '{}'::jsonb,
    TRUE,
    66
)
ON CONFLICT (setting_key) DO UPDATE
SET group_key = EXCLUDED.group_key,
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    value_type = EXCLUDED.value_type,
    field_type = EXCLUDED.field_type,
    default_value_json = EXCLUDED.default_value_json,
    options_json = EXCLUDED.options_json,
    validation_json = EXCLUDED.validation_json,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();
