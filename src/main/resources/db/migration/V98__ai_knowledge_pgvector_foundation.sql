-- ==========================================================
-- V98 - AI Knowledge pgvector Foundation
-- ==========================================================
-- Phase 1 semantic RAG foundation.
-- The initial embedding dimension is fixed at 768 because Flyway
-- SQL migrations cannot read Spring application properties at runtime.
-- If the embedding model changes dimension, create a follow-up migration
-- and reingest knowledge chunks for the new model.
--
-- Local development may run on PostgreSQL instances without pgvector
-- installed. RAG and embeddings are disabled by default, so the migration
-- must not block application startup in that state. When pgvector is
-- available this migration creates embedding vector(768) and HNSW. When it
-- is unavailable it creates the same tables with an embedding array fallback;
-- semantic ingestion/search still requires installing pgvector.
-- ==========================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_available_extensions
        WHERE name = 'vector'
    ) THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.ai_knowledge_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NULL,
    branch_id BIGINT NULL,
    module VARCHAR(100) NULL,
    language VARCHAR(20) NOT NULL DEFAULT 'en',
    document_type VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    source_uri TEXT NULL,
    content_hash VARCHAR(128) NULL,
    raw_content TEXT NULL,
    normalized_content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by_user_id BIGINT NULL,
    updated_by_user_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_knowledge_document_language
        CHECK (length(btrim(language)) > 0),
    CONSTRAINT chk_ai_knowledge_document_title
        CHECK (length(btrim(title)) > 0),
    CONSTRAINT chk_ai_knowledge_document_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'INGESTING', 'FAILED')),
    CONSTRAINT chk_ai_knowledge_document_scope
        CHECK (company_id IS NOT NULL OR branch_id IS NULL)
);

DO $$
DECLARE
    has_vector BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'vector'
    ) INTO has_vector;

    IF has_vector THEN
        EXECUTE $sql$
            CREATE TABLE IF NOT EXISTS public.ai_knowledge_chunk (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                document_id UUID NOT NULL,
                company_id BIGINT NULL,
                branch_id BIGINT NULL,
                module VARCHAR(100) NULL,
                language VARCHAR(20) NOT NULL DEFAULT 'en',
                chunk_index INTEGER NOT NULL,
                heading TEXT NULL,
                content TEXT NOT NULL,
                token_count INTEGER NOT NULL DEFAULT 0,
                embedding vector(768) NULL,
                embedding_model VARCHAR(120) NULL,
                status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                CONSTRAINT fk_ai_knowledge_chunk_document
                    FOREIGN KEY (document_id)
                    REFERENCES public.ai_knowledge_document (id)
                    ON DELETE CASCADE,
                CONSTRAINT chk_ai_knowledge_chunk_language
                    CHECK (length(btrim(language)) > 0),
                CONSTRAINT chk_ai_knowledge_chunk_index
                    CHECK (chunk_index >= 0),
                CONSTRAINT chk_ai_knowledge_chunk_token_count
                    CHECK (token_count >= 0),
                CONSTRAINT chk_ai_knowledge_chunk_content
                    CHECK (length(btrim(content)) > 0),
                CONSTRAINT chk_ai_knowledge_chunk_status
                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'FAILED')),
                CONSTRAINT chk_ai_knowledge_chunk_scope
                    CHECK (company_id IS NOT NULL OR branch_id IS NULL)
            )
        $sql$;
    ELSE
        EXECUTE $sql$
            CREATE TABLE IF NOT EXISTS public.ai_knowledge_chunk (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                document_id UUID NOT NULL,
                company_id BIGINT NULL,
                branch_id BIGINT NULL,
                module VARCHAR(100) NULL,
                language VARCHAR(20) NOT NULL DEFAULT 'en',
                chunk_index INTEGER NOT NULL,
                heading TEXT NULL,
                content TEXT NOT NULL,
                token_count INTEGER NOT NULL DEFAULT 0,
                embedding DOUBLE PRECISION[] NULL,
                embedding_model VARCHAR(120) NULL,
                status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                CONSTRAINT fk_ai_knowledge_chunk_document
                    FOREIGN KEY (document_id)
                    REFERENCES public.ai_knowledge_document (id)
                    ON DELETE CASCADE,
                CONSTRAINT chk_ai_knowledge_chunk_language
                    CHECK (length(btrim(language)) > 0),
                CONSTRAINT chk_ai_knowledge_chunk_index
                    CHECK (chunk_index >= 0),
                CONSTRAINT chk_ai_knowledge_chunk_token_count
                    CHECK (token_count >= 0),
                CONSTRAINT chk_ai_knowledge_chunk_content
                    CHECK (length(btrim(content)) > 0),
                CONSTRAINT chk_ai_knowledge_chunk_status
                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'FAILED')),
                CONSTRAINT chk_ai_knowledge_chunk_scope
                    CHECK (company_id IS NOT NULL OR branch_id IS NULL)
            )
        $sql$;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.ai_knowledge_ingestion_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    company_id BIGINT NULL,
    branch_id BIGINT NULL,
    status VARCHAR(30) NOT NULL,
    embedding_model VARCHAR(120) NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_knowledge_ingestion_job_document
        FOREIGN KEY (document_id)
        REFERENCES public.ai_knowledge_document (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_ai_knowledge_ingestion_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_ai_knowledge_ingestion_job_chunk_count
        CHECK (chunk_count >= 0),
    CONSTRAINT chk_ai_knowledge_ingestion_job_finished_at
        CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at),
    CONSTRAINT chk_ai_knowledge_ingestion_job_scope
        CHECK (company_id IS NOT NULL OR branch_id IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_scope
    ON public.ai_knowledge_document (company_id, branch_id, module, language, status);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_updated
    ON public.ai_knowledge_document (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_scope
    ON public.ai_knowledge_chunk (company_id, branch_id, module, language, status);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_document
    ON public.ai_knowledge_chunk (document_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_ingestion_job_document
    ON public.ai_knowledge_ingestion_job (document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_ingestion_job_scope
    ON public.ai_knowledge_ingestion_job (company_id, branch_id, status, created_at DESC);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_extension
        WHERE extname = 'vector'
    ) THEN
        EXECUTE $sql$
            CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_embedding_hnsw
                ON public.ai_knowledge_chunk
                USING hnsw (embedding vector_cosine_ops)
                WHERE embedding IS NOT NULL
        $sql$;
    END IF;
END
$$;
