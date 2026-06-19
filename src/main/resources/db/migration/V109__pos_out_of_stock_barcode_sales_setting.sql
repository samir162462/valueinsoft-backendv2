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
    'pos.allowOutOfStockBarcodeSales',
    'Allow out-of-stock barcode sales',
    'Allows barcode scans to add matched non-serialized products to the cart even when inventory quantity is zero.',
    'boolean',
    'switch',
    'false'::jsonb,
    '[]'::jsonb,
    '{}'::jsonb,
    TRUE,
    65
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
