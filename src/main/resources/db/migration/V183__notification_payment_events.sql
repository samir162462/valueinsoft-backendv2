-- Payment notifications for the obligations dashboard and POS checkout.
-- Financial push previews intentionally remain generic; full details are rendered
-- only inside the authenticated notification feed.

UPDATE public.notification_type_catalog
SET deep_link_template = 'valueinsoft://finance/payments/{transactionId}',
    updated_at = NOW()
WHERE type_key = 'finance.payment.received';

INSERT INTO public.notification_type_catalog (
    type_key,
    module_id,
    category,
    default_priority,
    default_channel_in_app,
    default_channel_push,
    push_preview_policy,
    group_key_template,
    aggregation_window_seconds,
    deep_link_template,
    required_capability,
    is_user_mutable,
    bypasses_quiet_hours,
    retention_days,
    preview_max_chars,
    producer_rate_limit_per_min,
    status
)
VALUES
    (
        'finance.payment.sent',
        'finance',
        'financial',
        'normal',
        TRUE,
        TRUE,
        'generic_only',
        NULL,
        0,
        'valueinsoft://finance/payments/{transactionId}',
        'finance.entry.read',
        TRUE,
        FALSE,
        1095,
        120,
        120,
        'active'
    ),
    (
        'pos.payment.received',
        'pos',
        'financial',
        'normal',
        TRUE,
        TRUE,
        'generic_only',
        NULL,
        0,
        'valueinsoft://pos/orders/{orderId}',
        'finance.entry.read',
        TRUE,
        FALSE,
        1095,
        120,
        120,
        'active'
    )
ON CONFLICT (type_key) DO UPDATE SET
    module_id = EXCLUDED.module_id,
    category = EXCLUDED.category,
    default_priority = EXCLUDED.default_priority,
    default_channel_in_app = EXCLUDED.default_channel_in_app,
    default_channel_push = EXCLUDED.default_channel_push,
    push_preview_policy = EXCLUDED.push_preview_policy,
    group_key_template = EXCLUDED.group_key_template,
    aggregation_window_seconds = EXCLUDED.aggregation_window_seconds,
    deep_link_template = EXCLUDED.deep_link_template,
    required_capability = EXCLUDED.required_capability,
    is_user_mutable = EXCLUDED.is_user_mutable,
    bypasses_quiet_hours = EXCLUDED.bypasses_quiet_hours,
    retention_days = EXCLUDED.retention_days,
    preview_max_chars = EXCLUDED.preview_max_chars,
    producer_rate_limit_per_min = EXCLUDED.producer_rate_limit_per_min,
    status = EXCLUDED.status,
    updated_at = NOW();

UPDATE public.notification_template
SET status = 'retired',
    retired_at = NOW()
WHERE type_key = 'finance.payment.received'
  AND status = 'published';

INSERT INTO public.notification_template (
    type_key,
    locale,
    template_version,
    title_template,
    body_template,
    preview_template,
    preview_generic,
    preview_reviewed_by,
    preview_reviewed_at,
    status,
    published_at,
    created_by
)
VALUES
    (
        'finance.payment.received',
        'en',
        2,
        'Payment received',
        'Payment {transactionId} for {amount} {currencyCode} was received. Recorded by {actorName}.',
        'A payment was received',
        'A payment was received',
        0,
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        'published',
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        0
    ),
    (
        'finance.payment.received',
        'ar',
        2,
        'تم استلام دفعة',
        'تم استلام الدفعة {transactionId} بمبلغ {amount} {currencyCode}. سجلها {actorName}.',
        'تم استلام دفعة',
        'تم استلام دفعة',
        0,
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        'published',
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        0
    ),
    (
        'finance.payment.sent',
        'en',
        1,
        'Payment sent',
        'Payment {transactionId} for {amount} {currencyCode} was sent. Recorded by {actorName}.',
        'A payment was sent',
        'A payment was sent',
        0,
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        'published',
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        0
    ),
    (
        'finance.payment.sent',
        'ar',
        1,
        'تم إرسال دفعة',
        'تم إرسال الدفعة {transactionId} بمبلغ {amount} {currencyCode}. سجلها {actorName}.',
        'تم إرسال دفعة',
        'تم إرسال دفعة',
        0,
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        'published',
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        0
    ),
    (
        'pos.payment.received',
        'en',
        1,
        'POS payment received',
        'POS payment {transactionId} for {amount} {currencyCode} was recorded. Settlement status: {settlementStatus}. Recorded by {actorName}.',
        'A POS payment was received',
        'A POS payment was received',
        0,
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        'published',
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        0
    ),
    (
        'pos.payment.received',
        'ar',
        1,
        'تم استلام دفعة نقطة بيع',
        'تم تسجيل دفعة نقطة البيع {transactionId} بمبلغ {amount} {currencyCode}. حالة السداد: {settlementStatus}. سجلها {actorName}.',
        'تم استلام دفعة نقطة بيع',
        'تم استلام دفعة نقطة بيع',
        0,
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        'published',
        TIMESTAMPTZ '2026-07-26 00:00:00+00',
        0
    );

DO $$
DECLARE
    active_payment_types INTEGER;
    published_payment_templates INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO active_payment_types
    FROM public.notification_type_catalog
    WHERE type_key IN (
        'finance.payment.received',
        'finance.payment.sent',
        'pos.payment.received'
    )
      AND status = 'active';

    SELECT COUNT(*)
    INTO published_payment_templates
    FROM public.notification_template
    WHERE type_key IN (
        'finance.payment.received',
        'finance.payment.sent',
        'pos.payment.received'
    )
      AND locale IN ('en', 'ar')
      AND status = 'published';

    IF active_payment_types <> 3 OR published_payment_templates <> 6 THEN
        RAISE EXCEPTION
            'Payment notification migration incomplete: % active types, % published templates',
            active_payment_types,
            published_payment_templates;
    END IF;
END;
$$;
