-- ==========================================================
-- V93 - AI Audit and Usage Logging
-- ==========================================================
-- Observability foundation for AI calls and controlled tool use.
-- Tool calls are not enabled yet.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.ai_tool_audit (
    id UUID PRIMARY KEY,
    conversation_id UUID NULL,
    company_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(150) NOT NULL,
    input_json TEXT,
    output_summary TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS public.ai_usage_log (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    conversation_id UUID NULL,
    model_name VARCHAR(100),
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost NUMERIC(19,6) NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_tool_audit_company_created
    ON public.ai_tool_audit (company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_tool_audit_conversation_created
    ON public.ai_tool_audit (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_usage_log_company_created
    ON public.ai_usage_log (company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_usage_log_user_created
    ON public.ai_usage_log (user_id, created_at DESC);
