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
VALUES
    (
        'receiptPrinter',
        'receiptPrinter.paperKind',
        'Default receipt paper',
        'Default paper layout used by POS receipt print previews and direct printing.',
        'enum',
        'select',
        '"receipt-80"'::jsonb,
        '[{"value":"receipt-80","label":"80mm receipt"},{"value":"receipt-58","label":"58mm receipt"},{"value":"a5","label":"A5 document"},{"value":"a4","label":"A4 document"}]'::jsonb,
        '{}'::jsonb,
        TRUE,
        40
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

UPDATE public.branch_setting_definitions
SET validation_json = '{"minValue":58,"maxValue":210}'::jsonb,
    sort_order = 45,
    updated_at = NOW()
WHERE setting_key = 'receiptPrinter.paperWidth';
