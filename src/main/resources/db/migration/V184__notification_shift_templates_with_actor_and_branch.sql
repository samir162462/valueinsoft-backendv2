-- =====================================================================
-- Shift open/close notifications: name the user and the branch.
--
-- V169 shipped these as "Shift {shiftId} was opened." — true, but useless on a
-- phone: it says neither who did it nor where. This publishes template_version 2
-- naming both.
--
-- Templates are IMMUTABLE once published (ADR-6). Editing version 1 in place
-- would silently rewrite the wording of every shift notification already sent,
-- which is exactly what the versioning exists to prevent. So version 1 is
-- retired and version 2 is published alongside it; historical recipient rows
-- keep pointing at version 1 and still render as they always did.
--
-- The push preview is deliberately NOT changed. pos.shift.* is
-- push_preview_policy = 'generic_only', so the lock screen keeps the param-free
-- string and no employee name leaves the authenticated surface (§3.2).
-- =====================================================================

-- Retire v1 first. uq_nt_published_per_type_locale permits exactly one published
-- row per (type_key, locale), so the insert below would fail otherwise.
UPDATE public.notification_template
   SET status = 'retired',
       retired_at = NOW()
 WHERE type_key IN ('pos.shift.opened', 'pos.shift.closed')
   AND template_version = 1
   AND status = 'published';

INSERT INTO public.notification_template (
    type_key, locale, template_version, title_template, body_template,
    preview_template, preview_generic, preview_reviewed_by, preview_reviewed_at,
    status, published_at, created_by
) VALUES
-- ── Shift opened ────────────────────────────────────────────────────
('pos.shift.opened', 'en', 2,
 'Shift opened',
 '{userName} opened shift {shiftId} at {branchName}.',
 'A POS shift was opened',
 'A POS shift was opened',
 0, NOW(), 'published', NOW(), 0),

('pos.shift.opened', 'ar', 2,
 'تم فتح الوردية',
 'قام {userName} بفتح الوردية {shiftId} في {branchName}.',
 'تم فتح وردية نقاط البيع',
 'تم فتح وردية نقاط البيع',
 0, NOW(), 'published', NOW(), 0),

-- ── Shift closed ────────────────────────────────────────────────────
('pos.shift.closed', 'en', 2,
 'Shift closed',
 '{userName} closed shift {shiftId} at {branchName}.',
 'A POS shift was closed',
 'A POS shift was closed',
 0, NOW(), 'published', NOW(), 0),

('pos.shift.closed', 'ar', 2,
 'تم إغلاق الوردية',
 'قام {userName} بإغلاق الوردية {shiftId} في {branchName}.',
 'تم إغلاق وردية نقاط البيع',
 'تم إغلاق وردية نقاط البيع',
 0, NOW(), 'published', NOW(), 0)

ON CONFLICT (type_key, locale, template_version) DO UPDATE SET
    title_template   = EXCLUDED.title_template,
    body_template    = EXCLUDED.body_template,
    preview_template = EXCLUDED.preview_template,
    preview_generic  = EXCLUDED.preview_generic,
    status           = EXCLUDED.status,
    published_at     = EXCLUDED.published_at;

-- Guard: exactly one published row per (type, locale) for both types. If this
-- fails the migration stops before the renderer is left ambiguous about which
-- version to use.
DO $$
DECLARE
    published_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO published_count
      FROM public.notification_template
     WHERE type_key IN ('pos.shift.opened', 'pos.shift.closed')
       AND status = 'published';

    IF published_count <> 4 THEN
        RAISE EXCEPTION
            'Expected 4 published shift templates (2 types x en/ar), found %',
            published_count;
    END IF;
END $$;
