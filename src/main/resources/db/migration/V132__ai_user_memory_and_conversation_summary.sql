-- ==========================================================
-- V132 - AI User Memory + Conversation Summary
-- ==========================================================
-- 1. Per-user long-term AI memory (preferences, language,
--    explicit "remember" notes) scoped by company + user.
-- 2. Rolling conversation summary so long chats keep context
--    beyond the recent-message window.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.ai_user_memory (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    memory_key VARCHAR(100) NOT NULL,
    memory_value TEXT NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'AUTO',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_ai_user_memory_scope UNIQUE (company_id, user_id, memory_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_user_memory_user_updated
    ON public.ai_user_memory (company_id, user_id, updated_at DESC);

ALTER TABLE public.ai_conversation
    ADD COLUMN IF NOT EXISTS summary TEXT;

ALTER TABLE public.ai_conversation
    ADD COLUMN IF NOT EXISTS summary_message_count INTEGER NOT NULL DEFAULT 0;
