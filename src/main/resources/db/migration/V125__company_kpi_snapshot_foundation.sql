-- ==========================================================
-- V125 - Company Smart Insights: KPI snapshot foundation
-- ==========================================================
-- Trusted, precomputed KPI snapshots (company-aware) that back the
-- Company Smart Insights (رؤى الشركة الذكية) admin dashboard.
--
-- Values are written by idempotent scheduled aggregation jobs using the
-- canonical KPI calculation layer (same expressions the dashboards use).
-- The admin read-path never recomputes raw invoices.
--
-- Grain:
--   branch_daily_kpi   : company x branch x business_date
--   company_daily_kpi  : company x business_date (aggregate of branch rows)
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.branch_daily_kpi (
    company_id            BIGINT       NOT NULL,
    branch_id             BIGINT       NOT NULL,
    business_date         DATE         NOT NULL,

    -- performance measures
    sales_amount          NUMERIC(16,2) NOT NULL DEFAULT 0,
    gross_profit_amount   NUMERIC(16,2) NOT NULL DEFAULT 0,
    gross_margin_pct      NUMERIC(7,2)  NOT NULL DEFAULT 0,
    orders_count          INTEGER       NOT NULL DEFAULT 0,
    avg_order_value       NUMERIC(16,2) NOT NULL DEFAULT 0,
    discount_amount       NUMERIC(16,2) NOT NULL DEFAULT 0,
    return_amount         NUMERIC(16,2) NOT NULL DEFAULT 0,
    expenses_amount       NUMERIC(16,2) NOT NULL DEFAULT 0,
    net_profit_amount     NUMERIC(16,2) NOT NULL DEFAULT 0,

    -- inventory measures
    inventory_value       NUMERIC(16,2) NOT NULL DEFAULT 0,
    inventory_quantity    NUMERIC(16,3) NOT NULL DEFAULT 0,
    low_stock_count       INTEGER       NOT NULL DEFAULT 0,
    out_of_stock_count    INTEGER       NOT NULL DEFAULT 0,
    dead_stock_value      NUMERIC(16,2) NOT NULL DEFAULT 0,
    stock_movement_count  INTEGER       NOT NULL DEFAULT 0,

    -- context / data-quality
    data_quality_status   VARCHAR(24)  NOT NULL DEFAULT 'COMPLETE', -- COMPLETE | PARTIAL | MISSING | SYNC_UNHEALTHY
    is_branch_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    operating_minutes_open INTEGER     NULL,
    source_version        INTEGER      NOT NULL DEFAULT 1,

    computed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_branch_daily_kpi PRIMARY KEY (company_id, branch_id, business_date)
);

CREATE INDEX IF NOT EXISTS idx_branch_daily_kpi_company_date
    ON public.branch_daily_kpi (company_id, business_date);

CREATE INDEX IF NOT EXISTS idx_branch_daily_kpi_company_branch_date
    ON public.branch_daily_kpi (company_id, branch_id, business_date DESC);

DROP TRIGGER IF EXISTS trg_branch_daily_kpi_set_updated_at ON public.branch_daily_kpi;
CREATE TRIGGER trg_branch_daily_kpi_set_updated_at
    BEFORE UPDATE ON public.branch_daily_kpi
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();


CREATE TABLE IF NOT EXISTS public.company_daily_kpi (
    company_id                 BIGINT       NOT NULL,
    business_date              DATE         NOT NULL,

    sales_amount               NUMERIC(16,2) NOT NULL DEFAULT 0,
    gross_profit_amount        NUMERIC(16,2) NOT NULL DEFAULT 0,
    gross_margin_pct           NUMERIC(7,2)  NOT NULL DEFAULT 0, -- recomputed from aggregated numerator/denominator
    orders_count               INTEGER       NOT NULL DEFAULT 0,
    avg_order_value            NUMERIC(16,2) NOT NULL DEFAULT 0, -- recomputed, never averaged of averages
    discount_amount            NUMERIC(16,2) NOT NULL DEFAULT 0,
    return_amount              NUMERIC(16,2) NOT NULL DEFAULT 0,
    expenses_amount            NUMERIC(16,2) NOT NULL DEFAULT 0,
    net_profit_amount          NUMERIC(16,2) NOT NULL DEFAULT 0,

    inventory_value            NUMERIC(16,2) NOT NULL DEFAULT 0,
    inventory_quantity         NUMERIC(16,3) NOT NULL DEFAULT 0,
    low_stock_count            INTEGER       NOT NULL DEFAULT 0,
    out_of_stock_count         INTEGER       NOT NULL DEFAULT 0,
    dead_stock_value           NUMERIC(16,2) NOT NULL DEFAULT 0,
    stock_movement_count       INTEGER       NOT NULL DEFAULT 0,

    branch_count               INTEGER       NOT NULL DEFAULT 0,
    branches_with_complete_data INTEGER      NOT NULL DEFAULT 0,

    source_version             INTEGER      NOT NULL DEFAULT 1,
    computed_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_company_daily_kpi PRIMARY KEY (company_id, business_date)
);

CREATE INDEX IF NOT EXISTS idx_company_daily_kpi_company_date
    ON public.company_daily_kpi (company_id, business_date DESC);

DROP TRIGGER IF EXISTS trg_company_daily_kpi_set_updated_at ON public.company_daily_kpi;
CREATE TRIGGER trg_company_daily_kpi_set_updated_at
    BEFORE UPDATE ON public.company_daily_kpi
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();
