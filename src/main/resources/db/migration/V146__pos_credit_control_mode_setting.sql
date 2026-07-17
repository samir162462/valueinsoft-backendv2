-- =====================================================================
-- V146: Branch setting for POS credit control (Stage 5.2 of
-- docs/ar-ap-credit-open-items/OPEN_ITEMS_IMPLEMENTATION_ROADMAP.md).
--
-- pos.creditControlMode governs what happens when a credit sale would
-- exceed the client's credit limit (exposure computed from ar_open_item
-- remaining minus unapplied ar_credit_note amounts, company-wide):
--   OFF   -> no check (default: safe rollout, matches today's behavior)
--   WARN  -> sale proceeds, POS shows the breach
--   BLOCK -> sale is rejected with CREDIT_LIMIT_EXCEEDED
-- Client credit_status HOLD/BLOCKED always denies new credit sales when
-- the mode is not OFF, regardless of WARN/BLOCK (status is a hard state,
-- the mode only softens LIMIT breaches).
--
-- Uses the V37 branch-settings foundation; same upsert shape as V124.
-- =====================================================================

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
    'pos.creditControlMode',
    'Credit sale limit control',
    'Controls credit (pay-later) sales against the client credit limit. OFF disables the check. WARN allows the sale but shows the breach at the point of sale. BLOCK rejects credit sales that would exceed the client limit; clients with credit status HOLD or BLOCKED are always denied new credit sales while the check is enabled.',
    'enum',
    'select',
    '"OFF"'::jsonb,
    '[{"value":"OFF","label":"Off (no check)"},{"value":"WARN","label":"Warn cashier"},{"value":"BLOCK","label":"Block sale"}]'::jsonb,
    '{}'::jsonb,
    TRUE,
    70
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
