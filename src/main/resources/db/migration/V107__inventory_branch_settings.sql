INSERT INTO public.branch_setting_groups (group_key, display_name, description, active, sort_order)
VALUES
    ('inventory', 'Inventory', 'Branch-level inventory thresholds, stock alerts, and inventory workspace behavior.', TRUE, 35)
ON CONFLICT (group_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW();

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
    ('inventory', 'inventory.lowStockThreshold', 'Low stock quantity threshold', 'Quantity at or below this value is considered low stock for inventory lists, audit, and dashboard alerts.', 'number', 'number', '5'::jsonb, '[]'::jsonb, '{"minValue":0,"maxValue":100000}'::jsonb, TRUE, 10),
    ('inventory', 'inventory.excludeSerializedFromLowStock', 'Exclude serialized items from low stock', 'Removes IMEI and serial-tracked products from low-stock counts and low-stock browse results.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 20),
    ('inventory', 'inventory.includeOutOfStockInLowStock', 'Count out of stock as low stock', 'Includes zero-quantity products when calculating low-stock inventory.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 30),
    ('inventory', 'inventory.lowStockCriticalCount', 'Critical low stock alert count', 'Dashboard low-stock alerts become critical when the low-stock item count reaches this value.', 'number', 'number', '10'::jsonb, '[]'::jsonb, '{"minValue":1,"maxValue":100000}'::jsonb, TRUE, 40),
    ('inventory', 'inventory.defaultBrowsePageSize', 'Default inventory page size', 'Default number of inventory rows requested by the modern inventory workspace.', 'number', 'number', '25'::jsonb, '[]'::jsonb, '{"minValue":10,"maxValue":200}'::jsonb, TRUE, 50),
    ('inventory', 'inventory.defaultBrowseSort', 'Default inventory sort', 'Default sort order for the modern inventory workspace.', 'enum', 'select', '"updatedAt:desc"'::jsonb, '[{"value":"updatedAt:desc","label":"Recently updated"},{"value":"quantityOnHand:asc","label":"Lowest stock first"},{"value":"quantityOnHand:desc","label":"Highest stock first"},{"value":"productName:asc","label":"Product name A-Z"}]'::jsonb, '{}'::jsonb, TRUE, 60),
    ('inventory', 'inventory.showSerializedUnitRows', 'Show serialized unit rows', 'Splits serialized products into unit rows in the modern inventory table.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 70),
    ('inventory', 'inventory.allowSupplierReturns', 'Allow supplier returns', 'Enables inventory bounce-back and supplier return actions when the user also has permission.', 'boolean', 'switch', 'true'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 80),
    ('inventory', 'inventory.auditDefaultLowStockOnly', 'Audit defaults to low stock only', 'Starts the inventory audit screen with low-stock filtering enabled.', 'boolean', 'switch', 'false'::jsonb, '[]'::jsonb, '{}'::jsonb, TRUE, 90)
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
