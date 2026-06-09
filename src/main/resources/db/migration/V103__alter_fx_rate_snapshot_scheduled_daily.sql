-- ==========================================================
-- V103 - Alter FX Rate Snapshot Scheduled Daily
-- ==========================================================
-- Replaces the weekly unique constraint on scheduled global FX rate snapshots
-- with a daily constraint on effective_date to support daily scheduled refreshes.
-- ==========================================================

DROP INDEX IF EXISTS public.uq_global_fx_rate_snapshot_scheduled_valid_week;

CREATE UNIQUE INDEX IF NOT EXISTS uq_global_fx_rate_snapshot_scheduled_valid_day
    ON public.global_fx_rate_snapshot (base_currency, target_currency, effective_date, source_code)
    WHERE status = 'VALID'
      AND validation_status = 'VALID'
      AND is_scheduled_rate = TRUE;
