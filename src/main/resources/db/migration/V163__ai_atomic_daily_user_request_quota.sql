-- Atomic per-user AI request quota. The primary key serializes concurrent
-- increments for the same tenant/user/day without application-level locks.
CREATE TABLE IF NOT EXISTS public.ai_user_daily_request_usage (
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ai_user_daily_request_usage
        PRIMARY KEY (company_id, user_id, usage_date),
    CONSTRAINT ck_ai_user_daily_request_usage_nonnegative
        CHECK (request_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ai_user_daily_request_usage_date
    ON public.ai_user_daily_request_usage (usage_date);
