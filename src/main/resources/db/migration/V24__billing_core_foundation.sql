CREATE TABLE IF NOT EXISTS public.billing_accounts (
    billing_account_id BIGSERIAL PRIMARY KEY,
    tenant_id INTEGER,
    company_id INTEGER NOT NULL,
    account_code VARCHAR(100),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    bill_to_name VARCHAR(255),
    bill_to_email VARCHAR(255),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_accounts_company UNIQUE (company_id),
    CONSTRAINT uq_billing_accounts_account_code UNIQUE (account_code),
    CONSTRAINT fk_billing_accounts_company FOREIGN KEY (company_id)
        REFERENCES public."Company" (id)
);

CREATE TABLE IF NOT EXISTS public.billing_prices (
    billing_price_id BIGSERIAL PRIMARY KEY,
    price_code VARCHAR(100) NOT NULL,
    package_id VARCHAR(100),
    display_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    billing_interval VARCHAR(32) NOT NULL DEFAULT 'monthly',
    branch_quantity_strategy VARCHAR(32) NOT NULL DEFAULT 'per_branch',
    unit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_prices_price_code UNIQUE (price_code)
);

CREATE TABLE IF NOT EXISTS public.branch_subscriptions (
    branch_subscription_id BIGSERIAL PRIMARY KEY,
    billing_account_id BIGINT NOT NULL,
    branch_id INTEGER NOT NULL,
    tenant_id INTEGER,
    legacy_subscription_id INTEGER,
    price_code VARCHAR(100),
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    billing_interval VARCHAR(32) NOT NULL DEFAULT 'monthly',
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    start_date DATE,
    current_period_start DATE,
    current_period_end DATE,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_branch_subscriptions_account FOREIGN KEY (billing_account_id)
        REFERENCES public.billing_accounts (billing_account_id),
    CONSTRAINT fk_branch_subscriptions_branch FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId")
);

CREATE TABLE IF NOT EXISTS public.billing_invoices (
    billing_invoice_id BIGSERIAL PRIMARY KEY,
    billing_account_id BIGINT NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    due_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    issued_at TIMESTAMPTZ,
    due_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    source_type VARCHAR(64),
    source_id VARCHAR(128),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_invoices_number UNIQUE (invoice_number),
    CONSTRAINT fk_billing_invoices_account FOREIGN KEY (billing_account_id)
        REFERENCES public.billing_accounts (billing_account_id)
);

CREATE TABLE IF NOT EXISTS public.billing_invoice_lines (
    billing_invoice_line_id BIGSERIAL PRIMARY KEY,
    billing_invoice_id BIGINT NOT NULL,
    branch_subscription_id BIGINT,
    line_type VARCHAR(64) NOT NULL DEFAULT 'subscription',
    line_description TEXT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    line_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_invoice_lines_invoice FOREIGN KEY (billing_invoice_id)
        REFERENCES public.billing_invoices (billing_invoice_id),
    CONSTRAINT fk_billing_invoice_lines_branch_subscription FOREIGN KEY (branch_subscription_id)
        REFERENCES public.branch_subscriptions (branch_subscription_id)
);

CREATE TABLE IF NOT EXISTS public.billing_payment_attempts (
    billing_payment_attempt_id BIGSERIAL PRIMARY KEY,
    billing_invoice_id BIGINT NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    external_payment_reference VARCHAR(255),
    external_order_id VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'created',
    requested_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'EGP',
    request_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    provider_response_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    failure_code VARCHAR(100),
    failure_message TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_billing_payment_attempts_invoice FOREIGN KEY (billing_invoice_id)
        REFERENCES public.billing_invoices (billing_invoice_id)
);

CREATE TABLE IF NOT EXISTS public.billing_provider_events (
    billing_provider_event_id BIGSERIAL PRIMARY KEY,
    provider_code VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    external_reference VARCHAR(255),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    processing_status VARCHAR(32) NOT NULL DEFAULT 'received',
    processed_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_provider_events UNIQUE (provider_code, provider_event_id)
);

CREATE TABLE IF NOT EXISTS public.billing_payment_methods (
    billing_payment_method_id BIGSERIAL PRIMARY KEY,
    billing_account_id BIGINT NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    provider_customer_reference VARCHAR(255),
    provider_payment_method_reference VARCHAR(255),
    method_type VARCHAR(50) NOT NULL DEFAULT 'card',
    masked_details VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_payment_methods_account FOREIGN KEY (billing_account_id)
        REFERENCES public.billing_accounts (billing_account_id)
);

CREATE TABLE IF NOT EXISTS public.billing_dunning_runs (
    billing_dunning_run_id BIGSERIAL PRIMARY KEY,
    billing_account_id BIGINT NOT NULL,
    billing_invoice_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'queued',
    attempt_number INTEGER NOT NULL DEFAULT 1,
    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMPTZ,
    result_summary TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_billing_dunning_runs_account FOREIGN KEY (billing_account_id)
        REFERENCES public.billing_accounts (billing_account_id),
    CONSTRAINT fk_billing_dunning_runs_invoice FOREIGN KEY (billing_invoice_id)
        REFERENCES public.billing_invoices (billing_invoice_id)
);

CREATE TABLE IF NOT EXISTS public.billing_entitlement_events (
    billing_entitlement_event_id BIGSERIAL PRIMARY KEY,
    branch_id INTEGER NOT NULL,
    branch_subscription_id BIGINT,
    billing_invoice_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32),
    reason_code VARCHAR(64),
    effective_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by_user_id INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_billing_entitlement_events_branch FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId"),
    CONSTRAINT fk_billing_entitlement_events_branch_subscription FOREIGN KEY (branch_subscription_id)
        REFERENCES public.branch_subscriptions (branch_subscription_id),
    CONSTRAINT fk_billing_entitlement_events_invoice FOREIGN KEY (billing_invoice_id)
        REFERENCES public.billing_invoices (billing_invoice_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_accounts_tenant_id
    ON public.billing_accounts (tenant_id);

CREATE INDEX IF NOT EXISTS idx_branch_subscriptions_branch_status
    ON public.branch_subscriptions (branch_id, status);

CREATE INDEX IF NOT EXISTS idx_branch_subscriptions_tenant_status
    ON public.branch_subscriptions (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_account_status
    ON public.billing_invoices (billing_account_id, status, due_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_payment_attempts_invoice_status
    ON public.billing_payment_attempts (billing_invoice_id, status, attempted_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_provider_events_processing
    ON public.billing_provider_events (processing_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_billing_entitlement_events_branch_effective_at
    ON public.billing_entitlement_events (branch_id, effective_at DESC);
