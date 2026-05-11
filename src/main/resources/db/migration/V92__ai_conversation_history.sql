-- ==========================================================
-- V92 - AI Conversation History
-- ==========================================================
-- Central/shared AI conversation and message tables.
-- Tenant business data stays in controlled backend services.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.ai_conversation (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    mode VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS public.ai_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    company_id BIGINT NOT NULL,
    branch_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ai_message_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES public.ai_conversation (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_conversation_company_user_updated
    ON public.ai_conversation (company_id, user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_message_conversation_created
    ON public.ai_message (conversation_id, created_at ASC);
