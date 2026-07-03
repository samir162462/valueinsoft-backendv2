-- ==========================================================
-- V128 - Company Smart Insights: job infrastructure
-- ==========================================================
-- company_insight_job_run           : idempotency + observability ledger for jobs.
-- company_insight_backfill_checkpoint: async, chunked, resumable, checkpointed backfill.
-- company_insight_dirty_queue        : debounced dirty-company/product tracking so
--                                      inventory insights recompute only affected slices
--                                      after stock movement (no full recompute per movement).
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.company_insight_job_run (
    id            BIGSERIAL PRIMARY KEY,
    job_name      VARCHAR(80)  NOT NULL,
    company_id    BIGINT       NOT NULL,
    business_date DATE         NULL,
    run_key       VARCHAR(160) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING', -- RUNNING | SUCCESS | FAILED | SKIPPED
    started_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ  NULL,
    rows_written  INTEGER      NOT NULL DEFAULT 0,
    error         TEXT         NULL,

    CONSTRAINT ux_company_insight_job_run UNIQUE (job_name, company_id, business_date, run_key)
);

CREATE INDEX IF NOT EXISTS idx_company_insight_job_run_company_started
    ON public.company_insight_job_run (company_id, started_at DESC);


CREATE TABLE IF NOT EXISTS public.company_insight_backfill_checkpoint (
    backfill_id   BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    grain         VARCHAR(24)  NOT NULL, -- branch | company | product
    cursor_date   DATE         NULL,     -- last completed date (exclusive frontier)
    range_from    DATE         NOT NULL,
    range_to      DATE         NOT NULL,
    chunk_status  VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING | RUNNING | DONE
    chunks_total  INTEGER      NOT NULL DEFAULT 0,
    chunks_done   INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING | RUNNING | PAUSED | COMPLETED | FAILED
    requested_by  BIGINT       NULL,
    error         TEXT         NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_company_insight_backfill_status
    ON public.company_insight_backfill_checkpoint (status, company_id);

DROP TRIGGER IF EXISTS trg_company_insight_backfill_set_updated_at ON public.company_insight_backfill_checkpoint;
CREATE TRIGGER trg_company_insight_backfill_set_updated_at
    BEFORE UPDATE ON public.company_insight_backfill_checkpoint
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();


CREATE TABLE IF NOT EXISTS public.company_insight_dirty_queue (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    branch_id     BIGINT       NULL,
    product_id    BIGINT       NULL,
    reason        VARCHAR(48)  NOT NULL, -- STOCK_MOVEMENT | RECEIPT | ADJUSTMENT | MANUAL
    enqueued_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    process_after TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ  NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0
);

-- Debounce: at most one unprocessed row per (company, product). Repeated movements
-- collapse by refreshing process_after (see InsightDirtyQueueService upsert).
CREATE UNIQUE INDEX IF NOT EXISTS ux_company_insight_dirty_unprocessed
    ON public.company_insight_dirty_queue (company_id, product_id)
    WHERE processed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_company_insight_dirty_ready
    ON public.company_insight_dirty_queue (process_after)
    WHERE processed_at IS NULL;
