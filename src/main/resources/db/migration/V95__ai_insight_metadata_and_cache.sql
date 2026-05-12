CREATE TABLE IF NOT EXISTS public.ai_table_catalog (
    id BIGSERIAL PRIMARY KEY,
    schema_name VARCHAR(128) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    business_meaning TEXT,
    aliases TEXT,
    tenant_scoped BOOLEAN NOT NULL DEFAULT TRUE,
    branch_scoped BOOLEAN NOT NULL DEFAULT FALSE,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (schema_name, table_name)
);

CREATE TABLE IF NOT EXISTS public.ai_column_catalog (
    id BIGSERIAL PRIMARY KEY,
    table_catalog_id BIGINT NOT NULL REFERENCES public.ai_table_catalog(id) ON DELETE CASCADE,
    column_name VARCHAR(128) NOT NULL,
    data_type VARCHAR(128) NOT NULL,
    business_meaning TEXT,
    is_primary_key BOOLEAN NOT NULL DEFAULT FALSE,
    is_foreign_key BOOLEAN NOT NULL DEFAULT FALSE,
    is_tenant_column BOOLEAN NOT NULL DEFAULT FALSE,
    is_branch_column BOOLEAN NOT NULL DEFAULT FALSE,
    is_date_column BOOLEAN NOT NULL DEFAULT FALSE,
    is_numeric_column BOOLEAN NOT NULL DEFAULT FALSE,
    include_in_ai BOOLEAN NOT NULL DEFAULT TRUE,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (table_catalog_id, column_name)
);

CREATE TABLE IF NOT EXISTS public.ai_query_templates (
    id BIGSERIAL PRIMARY KEY,
    intent_name VARCHAR(128) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    required_tables TEXT NOT NULL,
    required_columns TEXT NOT NULL,
    response_style TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.ai_chat_audit_log (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT,
    branch_id BIGINT,
    user_id BIGINT,
    conversation_id UUID,
    intent_name VARCHAR(128),
    strategy VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    row_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.ai_insight_cache (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    branch_id BIGINT,
    cache_key VARCHAR(128) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    metadata_json TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, branch_id, cache_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_column_catalog_table ON public.ai_column_catalog(table_catalog_id);
CREATE INDEX IF NOT EXISTS idx_ai_chat_audit_log_company_created ON public.ai_chat_audit_log(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_insight_cache_lookup ON public.ai_insight_cache(company_id, branch_id, cache_key, expires_at);

INSERT INTO public.ai_query_templates (intent_name, description, required_tables, required_columns, response_style)
VALUES
('top_selling_products', 'Best selling products for a selected period.', 'PosOrder_{branchId}, PosOrderDetail_{branchId}', 'orderTime, productId, itemName, quantity, total', 'Rank products by quantity and sales total.'),
('sales_summary', 'Sales totals for today, this week, or this month.', 'PosOrder_{branchId}', 'orderTime, orderTotal, orderDiscount, orderIncome, orderBouncedBack', 'Summarize orders, gross, discount, refunds, net, and income.'),
('low_stock_products', 'Products whose available stock is low.', 'inventory_product, inventory_branch_stock_balance', 'product_id, product_name, serial, quantity, reserved_qty', 'List products that need attention first.'),
('top_suppliers_by_payable', 'Suppliers with the largest payable balances.', 'supplier_{branchId}', 'supplierId, SupplierName, supplierRemainig', 'Rank suppliers by payable balance.'),
('supplier_products', 'Suppliers that provide the most products.', 'SupplierBProduct, supplier_{branchId}', 'supplierId, quantity, productId', 'Rank suppliers by product count and purchased quantity.'),
('sales_by_cashier', 'Sales grouped by cashier for a period.', 'PosOrder_{branchId}', 'salesUser, orderTotal, orderIncome, orderTime', 'Rank cashiers by sales and order count.')
ON CONFLICT (intent_name) DO UPDATE
SET description = EXCLUDED.description,
    required_tables = EXCLUDED.required_tables,
    required_columns = EXCLUDED.required_columns,
    response_style = EXCLUDED.response_style,
    updated_at = now();
