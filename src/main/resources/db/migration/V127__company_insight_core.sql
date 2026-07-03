-- ==========================================================
-- V127 - Company Smart Insights: insight core (atomic + lifecycle + correlation)
-- ==========================================================
-- ai_company_insight       : persisted, deterministic-first insights. AI only wraps
--                            narrative around backend-owned slots. Atomic insight_key
--                            (no volatile product-set fingerprint). Explicit lifecycle
--                            (dismissed never auto-reopens; resolved reopens only after
--                            cooldown + recurrence). occurrence_count / last_detected_at.
--                            action_code + action_context (no persisted frontend URLs).
--                            Correlation: PRIMARY vs CONTRIBUTING vs SUPPRESSED.
-- company_insight_settings : per-company thresholds (defaults applied in code when absent).
-- company_insight_audit_log: lifecycle + settings + admin-op trail.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.ai_company_insight (
    id                      BIGSERIAL PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    insight_type            VARCHAR(48)  NOT NULL, -- COMPANY_WEEKLY_PERFORMANCE | LOW_PERFORMING_BRANCH | COMPANY_WIDE_LOW_STOCK | DEAD_STOCK_COMPANY_WIDE | BRANCH_NO_ACTIVITY
    insight_key             VARCHAR(200) NOT NULL, -- atomic stable key
    period_type             VARCHAR(16)  NOT NULL, -- DAY | WEEK | MONTH | ROLLING
    period_start            DATE         NOT NULL,
    period_end              DATE         NOT NULL,

    severity                VARCHAR(16)  NOT NULL, -- INFO | WARNING | CRITICAL
    category                VARCHAR(32)  NOT NULL, -- PERFORMANCE | INVENTORY | ACTIVITY
    priority_score          INTEGER      NOT NULL DEFAULT 0,

    -- correlation
    role                    VARCHAR(16)  NOT NULL DEFAULT 'PRIMARY', -- PRIMARY | CONTRIBUTING | SUPPRESSED
    correlation_group       VARCHAR(200) NULL,
    parent_insight_id       BIGINT       NULL REFERENCES public.ai_company_insight(id) ON DELETE SET NULL,
    suppressed_reason       VARCHAR(48)  NULL, -- ROLLED_UP_INTO_PARENT | CATEGORY_CAP | LOWER_PRIORITY_DUP

    -- lifecycle
    status                  VARCHAR(16)  NOT NULL DEFAULT 'NEW', -- NEW | SEEN | DISMISSED | RESOLVED | EXPIRED
    occurrence_count        INTEGER      NOT NULL DEFAULT 1,
    first_detected_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_detected_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cooldown_until          TIMESTAMPTZ  NULL,

    -- content (deterministic source of truth)
    title                   TEXT         NOT NULL,
    description             TEXT         NOT NULL,
    executive_summary       TEXT         NULL,
    localized_json          JSONB        NOT NULL DEFAULT '{}'::jsonb, -- { "ar": {...}, "en": {...} }
    slots_json              JSONB        NOT NULL DEFAULT '{}'::jsonb, -- backend-owned slot values
    financial_impact        NUMERIC(16,2) NULL,
    affected_branch_ids     BIGINT[]     NOT NULL DEFAULT '{}',
    affected_product_ids    BIGINT[]     NOT NULL DEFAULT '{}',
    contributing_factors_json JSONB      NULL,

    -- action (structured, frontend maps to route + filters)
    action_code             VARCHAR(48)  NULL,
    action_context          JSONB        NULL,

    source_metrics_json     JSONB        NOT NULL DEFAULT '{}'::jsonb, -- immutable deterministic evidence
    data_quality_status     VARCHAR(24)  NOT NULL DEFAULT 'COMPLETE',
    enrichment_source       VARCHAR(16)  NOT NULL DEFAULT 'DETERMINISTIC', -- DETERMINISTIC | AI
    ai_model                VARCHAR(64)  NULL,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ  NULL,
    seen_at                 TIMESTAMPTZ  NULL,
    dismissed_at            TIMESTAMPTZ  NULL,
    resolved_at             TIMESTAMPTZ  NULL,

    CONSTRAINT ux_ai_company_insight_atomic
        UNIQUE (company_id, insight_type, insight_key, period_start)
);

CREATE INDEX IF NOT EXISTS idx_ai_company_insight_company_status
    ON public.ai_company_insight (company_id, status, severity, priority_score DESC);

CREATE INDEX IF NOT EXISTS idx_ai_company_insight_company_type_period
    ON public.ai_company_insight (company_id, insight_type, period_start DESC);

CREATE INDEX IF NOT EXISTS idx_ai_company_insight_company_group
    ON public.ai_company_insight (company_id, correlation_group);

CREATE INDEX IF NOT EXISTS idx_ai_company_insight_company_expires
    ON public.ai_company_insight (company_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_ai_company_insight_affected_branches
    ON public.ai_company_insight USING GIN (affected_branch_ids);

CREATE INDEX IF NOT EXISTS idx_ai_company_insight_affected_products
    ON public.ai_company_insight USING GIN (affected_product_ids);

DROP TRIGGER IF EXISTS trg_ai_company_insight_set_updated_at ON public.ai_company_insight;
CREATE TRIGGER trg_ai_company_insight_set_updated_at
    BEFORE UPDATE ON public.ai_company_insight
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();


CREATE TABLE IF NOT EXISTS public.company_insight_settings (
    config_id                          BIGSERIAL PRIMARY KEY,
    company_id                         BIGINT       NOT NULL,

    low_performing_branch_deviation_pct NUMERIC(6,2) NOT NULL DEFAULT 25.00,
    critical_stock_coverage_days       INTEGER      NOT NULL DEFAULT 7,
    low_stock_multi_branch_count       INTEGER      NOT NULL DEFAULT 2,
    dead_stock_no_sale_days            INTEGER      NOT NULL DEFAULT 60,
    dead_stock_min_value               NUMERIC(14,2) NOT NULL DEFAULT 1000,
    margin_drop_pct                    NUMERIC(6,2) NOT NULL DEFAULT 5.00,
    material_sales_drop_pct            NUMERIC(6,2) NOT NULL DEFAULT 15.00,
    no_sales_alert_delay_minutes       INTEGER      NOT NULL DEFAULT 180,
    min_history_days_for_comparison    INTEGER      NOT NULL DEFAULT 28,
    new_branch_grace_days              INTEGER      NOT NULL DEFAULT 30,
    max_active_insights_per_category   INTEGER      NOT NULL DEFAULT 5,
    insight_cooldown_hours             INTEGER      NOT NULL DEFAULT 72,

    currency_code                      VARCHAR(10)  NOT NULL DEFAULT 'EGP',
    timezone                           VARCHAR(80)  NOT NULL DEFAULT 'Africa/Cairo',
    ai_enrichment_enabled              BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at                         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT company_insight_settings_positive_ck CHECK (
        low_performing_branch_deviation_pct >= 0 AND low_performing_branch_deviation_pct <= 100
        AND margin_drop_pct >= 0 AND margin_drop_pct <= 100
        AND material_sales_drop_pct >= 0 AND material_sales_drop_pct <= 100
        AND critical_stock_coverage_days > 0
        AND low_stock_multi_branch_count > 0
        AND dead_stock_no_sale_days > 0
        AND dead_stock_min_value >= 0
        AND no_sales_alert_delay_minutes > 0
        AND min_history_days_for_comparison > 0
        AND new_branch_grace_days >= 0
        AND max_active_insights_per_category > 0
        AND insight_cooldown_hours >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_company_insight_settings_company
    ON public.company_insight_settings (company_id);

DROP TRIGGER IF EXISTS trg_company_insight_settings_set_updated_at ON public.company_insight_settings;
CREATE TRIGGER trg_company_insight_settings_set_updated_at
    BEFORE UPDATE ON public.company_insight_settings
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();


CREATE TABLE IF NOT EXISTS public.company_insight_audit_log (
    audit_id     BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    insight_id   BIGINT       NULL,
    user_id      BIGINT       NULL,
    event_type   VARCHAR(48)  NOT NULL, -- SEEN | DISMISSED | RESOLVED | SETTINGS_UPDATED | RECALC_TRIGGERED | BACKFILL_TRIGGERED
    input_json   TEXT         NULL,
    success      BOOLEAN      NOT NULL DEFAULT TRUE,
    duration_ms  BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_company_insight_audit_company_created
    ON public.company_insight_audit_log (company_id, created_at DESC);
