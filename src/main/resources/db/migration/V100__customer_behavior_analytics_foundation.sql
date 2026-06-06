CREATE TABLE IF NOT EXISTS public.customer_behavior_config (
    config_id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    branch_id BIGINT,
    new_customer_days INTEGER NOT NULL DEFAULT 30,
    active_customer_days INTEGER NOT NULL DEFAULT 60,
    at_risk_days INTEGER NOT NULL DEFAULT 90,
    dormant_days INTEGER NOT NULL DEFAULT 180,
    loyal_min_orders INTEGER NOT NULL DEFAULT 3,
    vip_min_orders INTEGER NOT NULL DEFAULT 5,
    vip_min_spend NUMERIC(14,2) NOT NULL DEFAULT 50000,
    discount_sensitive_ratio NUMERIC(7,4) NOT NULL DEFAULT 0.1500,
    return_risk_ratio NUMERIC(7,4) NOT NULL DEFAULT 0.1000,
    minimum_affinity_support INTEGER NOT NULL DEFAULT 3,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'EGP',
    timezone VARCHAR(80) NOT NULL DEFAULT 'Africa/Cairo',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT customer_behavior_config_positive_ck CHECK (
        new_customer_days > 0
        AND active_customer_days > 0
        AND at_risk_days > 0
        AND dormant_days > at_risk_days
        AND loyal_min_orders > 0
        AND vip_min_orders >= loyal_min_orders
        AND vip_min_spend >= 0
        AND discount_sensitive_ratio >= 0
        AND discount_sensitive_ratio <= 1
        AND return_risk_ratio >= 0
        AND return_risk_ratio <= 1
        AND minimum_affinity_support > 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_behavior_config_company
    ON public.customer_behavior_config (company_id)
    WHERE branch_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_behavior_config_company_branch
    ON public.customer_behavior_config (company_id, branch_id)
    WHERE branch_id IS NOT NULL;

DROP TRIGGER IF EXISTS trg_customer_behavior_config_set_updated_at
ON public.customer_behavior_config;

CREATE TRIGGER trg_customer_behavior_config_set_updated_at
BEFORE UPDATE ON public.customer_behavior_config
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.customer_behavior_audit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    branch_id BIGINT,
    user_id BIGINT,
    event_type VARCHAR(80) NOT NULL,
    input_json TEXT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_customer_behavior_audit_company_created
    ON public.customer_behavior_audit_log (company_id, created_at DESC);

INSERT INTO public.platform_capabilities (
    capability_key,
    module_id,
    resource,
    action,
    scope_type,
    status,
    description
) VALUES
    ('customer.behavior.view', 'clients', 'behavior', 'view', 'company', 'active', 'View Customer Purchase Behavior Analytics.'),
    ('customer.behavior.ai', 'clients', 'behavior', 'ai', 'company', 'active', 'Generate AI summaries for Customer Purchase Behavior Analytics.'),
    ('customer.behavior.export', 'clients', 'behavior', 'export', 'company', 'active', 'Export Customer Purchase Behavior Analytics.'),
    ('customer.behavior.configure', 'clients', 'behavior', 'configure', 'company', 'active', 'Configure Customer Purchase Behavior Analytics thresholds.')
ON CONFLICT (capability_key) DO UPDATE
SET module_id = EXCLUDED.module_id,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    scope_type = EXCLUDED.scope_type,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO public.role_grants (role_id, capability_key, scope_type, grant_mode, grant_version)
VALUES
    ('Owner', 'customer.behavior.view', 'company', 'allow', 'v1'),
    ('Owner', 'customer.behavior.ai', 'company', 'allow', 'v1'),
    ('Owner', 'customer.behavior.export', 'company', 'allow', 'v1'),
    ('Owner', 'customer.behavior.configure', 'company', 'allow', 'v1'),
    ('BranchManager', 'customer.behavior.view', 'branch', 'allow', 'v1'),
    ('BranchManager', 'customer.behavior.ai', 'branch', 'allow', 'v1'),
    ('BranchManager', 'customer.behavior.export', 'branch', 'allow', 'v1')
ON CONFLICT (role_id, capability_key, scope_type) DO UPDATE
SET grant_mode = EXCLUDED.grant_mode,
    grant_version = EXCLUDED.grant_version;

INSERT INTO public.ai_query_templates (intent_name, description, required_tables, required_columns, response_style)
VALUES
    ('customer_behavior_segments', 'Customer Purchase Behavior segment distribution using deterministic RFM and configured thresholds.', 'Client, PosOrder_{branchId}, PosOrderDetail_{branchId}', 'c_id, branchId, registeredTime, orderTime, clientId, orderTotal, orderDiscount, orderBouncedBack, quantity, total, bouncedBack', 'Summarize segment counts, spend, repeat rate, and risks from backend DTOs only.'),
    ('customer_preferences', 'Favorite products and categories for linked customers based on purchase lines.', 'PosOrder_{branchId}, PosOrderDetail_{branchId}, inventory_product, PosProduct_{branchId}', 'clientId, orderTime, productId, itemName, quantity, total, major, product_type, type', 'Rank products and categories by customer count, quantity, and spend.'),
    ('customer_purchase_patterns', 'RFM, average order value, purchase cadence, basket size, discount sensitivity, and return ratio for customers.', 'Client, PosOrder_{branchId}, PosOrderDetail_{branchId}', 'clientId, orderTime, orderTotal, orderDiscount, orderBouncedBack, quantity, bouncedBack', 'Explain trusted metrics and recommend actions without recalculating financial values.'),
    ('customer_affinity_products', 'Products frequently purchased together in linked-customer orders.', 'PosOrder_{branchId}, PosOrderDetail_{branchId}', 'orderId, clientId, productId, itemName, bouncedBack', 'Report product pairs, support, and confidence from backend-calculated DTOs.'),
    ('customer_retention_cohorts', 'First-purchase cohorts and repeat purchase rate for linked customers.', 'Client, PosOrder_{branchId}', 'clientId, orderTime, orderTotal, orderDiscount, orderBouncedBack', 'Summarize cohorts and repeat behavior from deterministic aggregates.')
ON CONFLICT (intent_name) DO UPDATE
SET description = EXCLUDED.description,
    required_tables = EXCLUDED.required_tables,
    required_columns = EXCLUDED.required_columns,
    response_style = EXCLUDED.response_style,
    updated_at = now();

DO $$
DECLARE
    schema_record RECORD;
    table_record RECORD;
BEGIN
    FOR schema_record IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c\_%' ESCAPE '\'
    LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I."Client" ("branchId")',
            'idx_' || schema_record.schema_name || '_client_branch', schema_record.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I."Client" ("branchId", "registeredTime" DESC)',
            'idx_' || schema_record.schema_name || '_client_branch_registered', schema_record.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I."Client" ("branchId", "clientPhone")',
            'idx_' || schema_record.schema_name || '_client_branch_phone', schema_record.schema_name);

        FOR table_record IN
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = schema_record.schema_name
              AND table_name LIKE 'PosOrder\_%' ESCAPE '\'
        LOOP
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("orderTime" DESC)',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_time',
                schema_record.schema_name,
                table_record.table_name);
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("clientId", "orderTime" DESC)',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_client_time',
                schema_record.schema_name,
                table_record.table_name);
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("clientId", "orderTime" DESC) WHERE "clientId" IS NOT NULL AND "clientId" > 0',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_valid_client_time',
                schema_record.schema_name,
                table_record.table_name);
        END LOOP;

        FOR table_record IN
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = schema_record.schema_name
              AND table_name LIKE 'PosOrderDetail\_%' ESCAPE '\'
        LOOP
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("orderId")',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_order',
                schema_record.schema_name,
                table_record.table_name);
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("productId")',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_product',
                schema_record.schema_name,
                table_record.table_name);
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("orderId", "productId")',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_order_product',
                schema_record.schema_name,
                table_record.table_name);
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.%I ("bouncedBack")',
                'idx_' || schema_record.schema_name || '_' || lower(table_record.table_name) || '_bounced',
                schema_record.schema_name,
                table_record.table_name);
        END LOOP;
    END LOOP;
END $$;
