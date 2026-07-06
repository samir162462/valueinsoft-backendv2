-- Metered AI billing: track cached input tokens (DeepSeek context cache) and the
-- USD cost alongside the EGP cost so tiny per-request amounts keep full precision.
ALTER TABLE public.ai_usage_log
    ADD COLUMN IF NOT EXISTS cached_prompt_tokens INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.ai_usage_log
    ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(19, 8) NOT NULL DEFAULT 0;
