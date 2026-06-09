-- ==========================================================
-- V102 - Global FX DeepSeek Rate Foundation
-- ==========================================================
-- Stores one global USD/EGP market-rate snapshot per retrieval
-- attempt, tenant-specific effective-rate calculations, and
-- guarded scheduler locks. Product prices are not changed by
-- this migration.
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.global_scheduler_lock (
    lock_name       VARCHAR(150) PRIMARY KEY,
    locked_until    TIMESTAMPTZ NOT NULL,
    locked_by       VARCHAR(150) NOT NULL,
    acquired_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_global_scheduler_lock_name CHECK (length(btrim(lock_name)) > 0),
    CONSTRAINT chk_global_scheduler_lock_locked_by CHECK (length(btrim(locked_by)) > 0)
);

DROP TRIGGER IF EXISTS trg_global_scheduler_lock_set_updated_at
ON public.global_scheduler_lock;

CREATE TRIGGER trg_global_scheduler_lock_set_updated_at
BEFORE UPDATE ON public.global_scheduler_lock
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.global_fx_rate_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    base_currency       VARCHAR(3) NOT NULL,
    target_currency     VARCHAR(3) NOT NULL,
    week_start_date     DATE NOT NULL,
    effective_date      DATE NULL,
    rate                NUMERIC(19, 8) NULL,
    rate_type           VARCHAR(40) NOT NULL DEFAULT 'REFERENCE',
    source_code         VARCHAR(60) NOT NULL DEFAULT 'DEEPSEEK',
    source_description  TEXT NULL,
    confidence          NUMERIC(5, 4) NULL,
    request_timestamp   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    response_timestamp  TIMESTAMPTZ NULL,
    raw_response        TEXT NULL,
    status              VARCHAR(20) NOT NULL,
    validation_status   VARCHAR(20) NOT NULL,
    validation_message  TEXT NULL,
    is_initial_rate     BOOLEAN NOT NULL DEFAULT FALSE,
    is_scheduled_rate   BOOLEAN NOT NULL DEFAULT FALSE,
    trigger_type        VARCHAR(40) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_global_fx_rate_snapshot_currency CHECK (
        base_currency = upper(base_currency)
        AND target_currency = upper(target_currency)
        AND length(base_currency) = 3
        AND length(target_currency) = 3
        AND base_currency <> target_currency
    ),
    CONSTRAINT chk_global_fx_rate_snapshot_status CHECK (
        status IN ('PENDING', 'VALID', 'REJECTED', 'FAILED', 'SUPERSEDED')
    ),
    CONSTRAINT chk_global_fx_rate_snapshot_validation_status CHECK (
        validation_status IN ('PENDING', 'VALID', 'REJECTED', 'FAILED')
    ),
    CONSTRAINT chk_global_fx_rate_snapshot_trigger CHECK (
        trigger_type IN ('INITIALIZATION', 'SCHEDULED', 'MANUAL')
    ),
    CONSTRAINT chk_global_fx_rate_snapshot_valid_rate CHECK (
        status <> 'VALID' OR (rate IS NOT NULL AND rate > 0 AND effective_date IS NOT NULL)
    ),
    CONSTRAINT chk_global_fx_rate_snapshot_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    )
);

CREATE INDEX IF NOT EXISTS idx_global_fx_rate_snapshot_pair_created
    ON public.global_fx_rate_snapshot (base_currency, target_currency, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_global_fx_rate_snapshot_valid_pair_week
    ON public.global_fx_rate_snapshot (base_currency, target_currency, week_start_date DESC)
    WHERE status = 'VALID' AND validation_status = 'VALID';

CREATE UNIQUE INDEX IF NOT EXISTS uq_global_fx_rate_snapshot_scheduled_valid_week
    ON public.global_fx_rate_snapshot (base_currency, target_currency, week_start_date, source_code)
    WHERE status = 'VALID'
      AND validation_status = 'VALID'
      AND is_scheduled_rate = TRUE;

DROP TRIGGER IF EXISTS trg_global_fx_rate_snapshot_set_updated_at
ON public.global_fx_rate_snapshot;

CREATE TRIGGER trg_global_fx_rate_snapshot_set_updated_at
BEFORE UPDATE ON public.global_fx_rate_snapshot
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.company_fx_pricing_config (
    company_id                  INTEGER PRIMARY KEY,
    fx_pricing_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    safety_buffer_percentage    NUMERIC(9, 4) NOT NULL DEFAULT 0,
    selected_rate_type          VARCHAR(40) NOT NULL DEFAULT 'REFERENCE',
    margin_rules_json           JSONB NOT NULL DEFAULT '{}'::jsonb,
    rounding_rules_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
    repricing_threshold_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_version              INTEGER NOT NULL DEFAULT 1,
    updated_by                  VARCHAR(150) NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_company_fx_pricing_config_tenant
        FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_company_fx_pricing_config_buffer CHECK (
        safety_buffer_percentage >= 0 AND safety_buffer_percentage <= 100
    ),
    CONSTRAINT chk_company_fx_pricing_config_version CHECK (config_version > 0),
    CONSTRAINT chk_company_fx_pricing_config_json CHECK (
        jsonb_typeof(margin_rules_json) = 'object'
        AND jsonb_typeof(rounding_rules_json) = 'object'
        AND jsonb_typeof(repricing_threshold_json) = 'object'
    )
);

DROP TRIGGER IF EXISTS trg_company_fx_pricing_config_set_updated_at
ON public.company_fx_pricing_config;

CREATE TRIGGER trg_company_fx_pricing_config_set_updated_at
BEFORE UPDATE ON public.company_fx_pricing_config
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.company_fx_effective_rate (
    id                          BIGSERIAL PRIMARY KEY,
    company_id                  INTEGER NOT NULL,
    global_fx_snapshot_id       BIGINT NOT NULL,
    safety_buffer_percentage    NUMERIC(9, 4) NOT NULL,
    effective_pricing_rate      NUMERIC(19, 8) NOT NULL,
    selected_rate_type          VARCHAR(40) NOT NULL,
    config_version              INTEGER NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'VALID',
    calculation_timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_company_fx_effective_rate_tenant
        FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_company_fx_effective_rate_snapshot
        FOREIGN KEY (global_fx_snapshot_id)
        REFERENCES public.global_fx_rate_snapshot (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_company_fx_effective_rate_snapshot UNIQUE (company_id, global_fx_snapshot_id),
    CONSTRAINT chk_company_fx_effective_rate_values CHECK (
        safety_buffer_percentage >= 0
        AND effective_pricing_rate > 0
        AND config_version > 0
    ),
    CONSTRAINT chk_company_fx_effective_rate_status CHECK (
        status IN ('VALID', 'FAILED', 'SKIPPED')
    )
);

DROP TRIGGER IF EXISTS trg_company_fx_effective_rate_set_updated_at
ON public.company_fx_effective_rate;

CREATE TRIGGER trg_company_fx_effective_rate_set_updated_at
BEFORE UPDATE ON public.company_fx_effective_rate
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.global_fx_company_processing_result (
    id                              BIGSERIAL PRIMARY KEY,
    global_fx_snapshot_id           BIGINT NOT NULL,
    company_id                      INTEGER NOT NULL,
    status                          VARCHAR(20) NOT NULL,
    safety_buffer_percentage        NUMERIC(9, 4) NULL,
    effective_pricing_rate          NUMERIC(19, 8) NULL,
    evaluated_products              INTEGER NOT NULL DEFAULT 0,
    recommendation_runs             INTEGER NOT NULL DEFAULT 0,
    recommendations_generated       INTEGER NOT NULL DEFAULT 0,
    skipped_reason                  TEXT NULL,
    failure_message                 TEXT NULL,
    started_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at                    TIMESTAMPTZ NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_global_fx_company_processing_snapshot
        FOREIGN KEY (global_fx_snapshot_id)
        REFERENCES public.global_fx_rate_snapshot (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_global_fx_company_processing_tenant
        FOREIGN KEY (company_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_global_fx_company_processing_result UNIQUE (global_fx_snapshot_id, company_id),
    CONSTRAINT chk_global_fx_company_processing_status CHECK (
        status IN ('SUCCESS', 'FAILED', 'SKIPPED', 'PARTIAL')
    ),
    CONSTRAINT chk_global_fx_company_processing_counts CHECK (
        evaluated_products >= 0
        AND recommendation_runs >= 0
        AND recommendations_generated >= 0
    )
);

DROP TRIGGER IF EXISTS trg_global_fx_company_processing_result_set_updated_at
ON public.global_fx_company_processing_result;

CREATE TRIGGER trg_global_fx_company_processing_result_set_updated_at
BEFORE UPDATE ON public.global_fx_company_processing_result
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE OR REPLACE FUNCTION public.create_inventory_fx_pricing_tables_for_tenant(schema_name text)
RETURNS void AS $$
BEGIN
    IF schema_name IS NULL OR schema_name NOT LIKE 'c\_%' ESCAPE '\' THEN
        RAISE EXCEPTION 'Inventory FX tenant schema must start with c_: %', schema_name;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = schema_name
          AND table_name = 'inventory_product'
    ) THEN
        RETURN;
    END IF;

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            ADD COLUMN IF NOT EXISTS fx_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
            ADD COLUMN IF NOT EXISTS replacement_cost_usd NUMERIC(19,4),
            ADD COLUMN IF NOT EXISTS replacement_cost_currency VARCHAR(3) NOT NULL DEFAULT ''USD'',
            ADD COLUMN IF NOT EXISTS purchase_usd_rate NUMERIC(19,8),
            ADD COLUMN IF NOT EXISTS replacement_cost_updated_at TIMESTAMPTZ
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            DROP CONSTRAINT IF EXISTS inventory_product_replacement_cost_ck,
            ADD CONSTRAINT inventory_product_replacement_cost_ck CHECK (
                replacement_cost_usd IS NULL OR replacement_cost_usd > 0
            )
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            DROP CONSTRAINT IF EXISTS inventory_product_replacement_currency_ck,
            ADD CONSTRAINT inventory_product_replacement_currency_ck CHECK (
                replacement_cost_currency = upper(replacement_cost_currency)
                AND length(replacement_cost_currency) = 3
            )
    ', schema_name);

    EXECUTE format('
        ALTER TABLE %I.inventory_product
            DROP CONSTRAINT IF EXISTS inventory_product_purchase_usd_rate_ck,
            ADD CONSTRAINT inventory_product_purchase_usd_rate_ck CHECK (
                purchase_usd_rate IS NULL OR purchase_usd_rate > 0
            )
    ', schema_name);

    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.inventory_fx_product_impact (
            impact_id                   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            global_fx_snapshot_id        BIGINT NOT NULL,
            company_id                   BIGINT NOT NULL,
            branch_id                    BIGINT NOT NULL,
            product_id                   BIGINT NOT NULL,
            product_name                 VARCHAR(200) NOT NULL,
            replacement_cost_usd         NUMERIC(19,4) NOT NULL,
            effective_pricing_rate       NUMERIC(19,8) NOT NULL,
            replacement_cost_egp         NUMERIC(19,4) NOT NULL,
            current_buying_price         NUMERIC(19,4) NOT NULL,
            cost_delta_amount            NUMERIC(19,4) NOT NULL,
            cost_delta_pct               NUMERIC(9,4),
            recommendation_run_id        BIGINT,
            recommendation_status        VARCHAR(40),
            created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            CONSTRAINT fk_inventory_fx_product_impact_snapshot
                FOREIGN KEY (global_fx_snapshot_id)
                REFERENCES public.global_fx_rate_snapshot (id)
                ON DELETE CASCADE,
            CONSTRAINT fk_inventory_fx_product_impact_product
                FOREIGN KEY (product_id)
                REFERENCES %I.inventory_product (product_id)
                ON DELETE CASCADE,
            CONSTRAINT chk_inventory_fx_product_impact_money CHECK (
                replacement_cost_usd > 0
                AND effective_pricing_rate > 0
                AND replacement_cost_egp > 0
                AND current_buying_price >= 0
            )
        )
    ', schema_name, schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.inventory_product (fx_pricing_enabled, replacement_cost_currency, product_id)
        WHERE fx_pricing_enabled = TRUE
    ', 'idx_inventory_product_fx_pricing', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.inventory_fx_product_impact (global_fx_snapshot_id, company_id, branch_id)
    ', 'idx_inventory_fx_product_impact_snapshot', schema_name);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I
        ON %I.inventory_fx_product_impact (product_id, created_at DESC)
    ', 'idx_inventory_fx_product_impact_product', schema_name);
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    schema_rec RECORD;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c\_%' ESCAPE '\'
        ORDER BY schema_name
    LOOP
        PERFORM public.create_inventory_fx_pricing_tables_for_tenant(schema_rec.schema_name);
    END LOOP;
END $$;

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('platform.fx.read', 'web_admin', 'global_fx_rate', 'read', 'global_admin', 'active', 'Read global FX rate snapshots and processing status.'),
    ('platform.fx.refresh', 'web_admin', 'global_fx_rate', 'refresh', 'global_admin', 'active', 'Manually refresh the global USD/EGP FX rate through the configured backend provider.')
ON CONFLICT (capability_key) DO UPDATE
SET
    module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = NOW();

INSERT INTO public.role_grants (
    role_id,
    capability_key,
    scope_type,
    grant_mode,
    grant_version
) VALUES
    ('SupportAdmin', 'platform.fx.read', 'global_admin', 'allow', 'v1'),
    ('SupportAdmin', 'platform.fx.refresh', 'global_admin', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET
    grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;
