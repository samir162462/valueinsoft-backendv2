-- ==========================================================
-- V126 - Company Smart Insights: product-grain KPI + inventory snapshot
-- ==========================================================
-- branch_product_daily_kpi : company x branch x business_date x product
--   Highest-volume table -> shortest retention (see retention job, ~90 days).
-- company_inventory_snapshot : company x snapshot_date x product (cross-branch rollup)
--   Directly feeds COMPANY_WIDE_LOW_STOCK and DEAD_STOCK_COMPANY_WIDE rules.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.branch_product_daily_kpi (
    company_id          BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    business_date       DATE         NOT NULL,
    product_id          BIGINT       NOT NULL,

    sold_qty            NUMERIC(16,3) NOT NULL DEFAULT 0,
    sold_amount         NUMERIC(16,2) NOT NULL DEFAULT 0,
    gross_profit_amount NUMERIC(16,2) NOT NULL DEFAULT 0,
    return_qty          NUMERIC(16,3) NOT NULL DEFAULT 0,
    movement_count      INTEGER       NOT NULL DEFAULT 0,
    on_hand_qty         NUMERIC(16,3) NOT NULL DEFAULT 0,
    on_hand_value       NUMERIC(16,2) NOT NULL DEFAULT 0,
    reorder_level       NUMERIC(19,4) NULL,
    last_sale_date      DATE         NULL,
    last_movement_date  DATE         NULL,

    source_version      INTEGER      NOT NULL DEFAULT 1,
    computed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_branch_product_daily_kpi PRIMARY KEY (company_id, branch_id, business_date, product_id)
);

CREATE INDEX IF NOT EXISTS idx_branch_product_daily_kpi_company_date_product
    ON public.branch_product_daily_kpi (company_id, business_date, product_id);

CREATE INDEX IF NOT EXISTS idx_branch_product_daily_kpi_company_product_date
    ON public.branch_product_daily_kpi (company_id, product_id, business_date DESC);

DROP TRIGGER IF EXISTS trg_branch_product_daily_kpi_set_updated_at ON public.branch_product_daily_kpi;
CREATE TRIGGER trg_branch_product_daily_kpi_set_updated_at
    BEFORE UPDATE ON public.branch_product_daily_kpi
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();


CREATE TABLE IF NOT EXISTS public.company_inventory_snapshot (
    company_id              BIGINT       NOT NULL,
    snapshot_date           DATE         NOT NULL,
    product_id              BIGINT       NOT NULL,

    total_qty               NUMERIC(16,3) NOT NULL DEFAULT 0,
    total_value             NUMERIC(16,2) NOT NULL DEFAULT 0,
    branch_count_with_stock INTEGER      NOT NULL DEFAULT 0,
    branches_below_reorder  INTEGER      NOT NULL DEFAULT 0,
    branches_out_of_stock   INTEGER      NOT NULL DEFAULT 0,
    last_sale_date          DATE         NULL,
    last_movement_date      DATE         NULL,
    is_dead_stock           BOOLEAN      NOT NULL DEFAULT FALSE,

    source_version          INTEGER      NOT NULL DEFAULT 1,
    computed_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_company_inventory_snapshot PRIMARY KEY (company_id, snapshot_date, product_id)
);

CREATE INDEX IF NOT EXISTS idx_company_inventory_snapshot_company_date
    ON public.company_inventory_snapshot (company_id, snapshot_date);

CREATE INDEX IF NOT EXISTS idx_company_inventory_snapshot_deadstock
    ON public.company_inventory_snapshot (company_id, snapshot_date, is_dead_stock)
    WHERE is_dead_stock = TRUE;

CREATE INDEX IF NOT EXISTS idx_company_inventory_snapshot_low
    ON public.company_inventory_snapshot (company_id, snapshot_date, branches_below_reorder);

DROP TRIGGER IF EXISTS trg_company_inventory_snapshot_set_updated_at ON public.company_inventory_snapshot;
CREATE TRIGGER trg_company_inventory_snapshot_set_updated_at
    BEFORE UPDATE ON public.company_inventory_snapshot
    FOR EACH ROW
    EXECUTE PROCEDURE valueinsoft_set_updated_at();
