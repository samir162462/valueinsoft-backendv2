CREATE TABLE IF NOT EXISTS public.branch_runtime_states (
    branch_id INTEGER PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    status_reason TEXT NULL,
    locked_at TIMESTAMPTZ NULL,
    locked_by_user_id INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_branch_runtime_states_branch
        FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId")
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_branch_runtime_states_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_branch_runtime_states_locked_by
        FOREIGN KEY (locked_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_branch_runtime_states_branch_id
        CHECK (branch_id > 0),
    CONSTRAINT chk_branch_runtime_states_tenant_id
        CHECK (tenant_id > 0),
    CONSTRAINT chk_branch_runtime_states_status
        CHECK (status IN ('active', 'locked', 'inactive', 'archived')),
    CONSTRAINT chk_branch_runtime_states_status_reason
        CHECK (status_reason IS NULL OR length(btrim(status_reason)) > 0)
);

COMMENT ON TABLE public.branch_runtime_states IS
    'Platform-level branch lifecycle state separate from branch subscription state.';

CREATE INDEX IF NOT EXISTS idx_branch_runtime_states_tenant_id
    ON public.branch_runtime_states (tenant_id);

CREATE INDEX IF NOT EXISTS idx_branch_runtime_states_status
    ON public.branch_runtime_states (status);

DROP TRIGGER IF EXISTS trg_branch_runtime_states_set_updated_at
ON public.branch_runtime_states;

CREATE TRIGGER trg_branch_runtime_states_set_updated_at
BEFORE UPDATE ON public.branch_runtime_states
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

INSERT INTO public.branch_runtime_states (
    branch_id,
    tenant_id,
    status,
    status_reason
)
SELECT
    b."branchId",
    b."companyId",
    'active',
    'bootstrap'
FROM public."Branch" b
JOIN public.tenants t
    ON t.tenant_id = b."companyId"
LEFT JOIN public.branch_runtime_states brs
    ON brs.branch_id = b."branchId"
WHERE brs.branch_id IS NULL;

CREATE OR REPLACE FUNCTION valueinsoft_sync_branch_runtime_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.tenants t
        WHERE t.tenant_id = NEW."companyId"
    ) THEN
        INSERT INTO public.branch_runtime_states (
            branch_id,
            tenant_id,
            status,
            status_reason
        ) VALUES (
            NEW."branchId",
            NEW."companyId",
            'active',
            'branch_insert'
        )
        ON CONFLICT (branch_id) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_branch_runtime_state_after_insert
ON public."Branch";

CREATE TRIGGER trg_branch_runtime_state_after_insert
AFTER INSERT ON public."Branch"
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_sync_branch_runtime_state();

CREATE TABLE IF NOT EXISTS public.tenant_lifecycle_events (
    event_id BIGSERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    previous_status TEXT NULL,
    new_status TEXT NOT NULL,
    reason TEXT NULL,
    note TEXT NULL,
    actor_user_id INTEGER NULL,
    actor_user_name TEXT NULL,
    source TEXT NOT NULL DEFAULT 'platform_admin',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenant_lifecycle_events_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_tenant_lifecycle_events_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_tenant_lifecycle_events_event_type
        CHECK (event_type IN ('status_change', 'suspend', 'resume', 'archive', 'activate')),
    CONSTRAINT chk_tenant_lifecycle_events_new_status
        CHECK (new_status IN ('onboarding', 'active', 'suspended', 'archived')),
    CONSTRAINT chk_tenant_lifecycle_events_source
        CHECK (source IN ('platform_admin', 'support', 'migration', 'system'))
);

COMMENT ON TABLE public.tenant_lifecycle_events IS
    'Immutable tenant lifecycle history for platform operations and governance.';

CREATE INDEX IF NOT EXISTS idx_tenant_lifecycle_events_tenant_id
    ON public.tenant_lifecycle_events (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_lifecycle_events_created_at
    ON public.tenant_lifecycle_events (created_at DESC);

CREATE TABLE IF NOT EXISTS public.branch_lifecycle_events (
    event_id BIGSERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    previous_status TEXT NULL,
    new_status TEXT NOT NULL,
    reason TEXT NULL,
    note TEXT NULL,
    actor_user_id INTEGER NULL,
    actor_user_name TEXT NULL,
    source TEXT NOT NULL DEFAULT 'platform_admin',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_branch_lifecycle_events_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_branch_lifecycle_events_branch
        FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId")
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_branch_lifecycle_events_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_branch_lifecycle_events_event_type
        CHECK (event_type IN ('status_change', 'lock', 'unlock', 'deactivate', 'activate')),
    CONSTRAINT chk_branch_lifecycle_events_new_status
        CHECK (new_status IN ('active', 'locked', 'inactive', 'archived')),
    CONSTRAINT chk_branch_lifecycle_events_source
        CHECK (source IN ('platform_admin', 'support', 'migration', 'system'))
);

COMMENT ON TABLE public.branch_lifecycle_events IS
    'Immutable branch lifecycle history for platform operations and governance.';

CREATE INDEX IF NOT EXISTS idx_branch_lifecycle_events_tenant_id
    ON public.branch_lifecycle_events (tenant_id);

CREATE INDEX IF NOT EXISTS idx_branch_lifecycle_events_branch_id
    ON public.branch_lifecycle_events (branch_id);

CREATE INDEX IF NOT EXISTS idx_branch_lifecycle_events_created_at
    ON public.branch_lifecycle_events (created_at DESC);

CREATE TABLE IF NOT EXISTS public.platform_admin_audit_log (
    event_id BIGSERIAL PRIMARY KEY,
    actor_user_id INTEGER NULL,
    actor_user_name TEXT NOT NULL,
    capability_key TEXT NULL,
    action_type TEXT NOT NULL,
    target_tenant_id INTEGER NULL,
    target_branch_id INTEGER NULL,
    request_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    context_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_status TEXT NOT NULL,
    correlation_id TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_admin_audit_log_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_platform_admin_audit_log_tenant
        FOREIGN KEY (target_tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_platform_admin_audit_log_branch
        FOREIGN KEY (target_branch_id)
        REFERENCES public."Branch" ("branchId")
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_platform_admin_audit_log_actor_user_name
        CHECK (length(btrim(actor_user_name)) > 0),
    CONSTRAINT chk_platform_admin_audit_log_action_type
        CHECK (action_type ~ '^[a-z][a-z0-9_\\.]*$'),
    CONSTRAINT chk_platform_admin_audit_log_result_status
        CHECK (result_status IN ('success', 'rejected', 'failed')),
    CONSTRAINT chk_platform_admin_audit_log_request_summary_object
        CHECK (jsonb_typeof(request_summary) = 'object'),
    CONSTRAINT chk_platform_admin_audit_log_context_summary_object
        CHECK (jsonb_typeof(context_summary) = 'object')
);

COMMENT ON TABLE public.platform_admin_audit_log IS
    'Platform-level audit log for administrative actions and sensitive inspections.';

CREATE INDEX IF NOT EXISTS idx_platform_admin_audit_log_created_at
    ON public.platform_admin_audit_log (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_admin_audit_log_actor_user_id
    ON public.platform_admin_audit_log (actor_user_id);

CREATE INDEX IF NOT EXISTS idx_platform_admin_audit_log_target_tenant_id
    ON public.platform_admin_audit_log (target_tenant_id);

CREATE TABLE IF NOT EXISTS public.platform_support_notes (
    note_id BIGSERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    branch_id INTEGER NULL,
    note_type TEXT NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    visibility TEXT NOT NULL DEFAULT 'internal',
    created_by_user_id INTEGER NULL,
    created_by_user_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_platform_support_notes_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_platform_support_notes_branch
        FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId")
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_platform_support_notes_created_by
        FOREIGN KEY (created_by_user_id)
        REFERENCES public.users (id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_platform_support_notes_note_type
        CHECK (note_type IN ('support', 'billing', 'risk', 'ops', 'follow_up')),
    CONSTRAINT chk_platform_support_notes_subject
        CHECK (length(btrim(subject)) > 0),
    CONSTRAINT chk_platform_support_notes_body
        CHECK (length(btrim(body)) > 0),
    CONSTRAINT chk_platform_support_notes_visibility
        CHECK (visibility IN ('internal', 'restricted')),
    CONSTRAINT chk_platform_support_notes_created_by_name
        CHECK (length(btrim(created_by_user_name)) > 0)
);

COMMENT ON TABLE public.platform_support_notes IS
    'Platform support notes for tenants and optional branches.';

CREATE INDEX IF NOT EXISTS idx_platform_support_notes_tenant_id
    ON public.platform_support_notes (tenant_id);

CREATE INDEX IF NOT EXISTS idx_platform_support_notes_branch_id
    ON public.platform_support_notes (branch_id);

CREATE INDEX IF NOT EXISTS idx_platform_support_notes_created_at
    ON public.platform_support_notes (created_at DESC);

DROP TRIGGER IF EXISTS trg_platform_support_notes_set_updated_at
ON public.platform_support_notes;

CREATE TRIGGER trg_platform_support_notes_set_updated_at
BEFORE UPDATE ON public.platform_support_notes
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.tenant_daily_metrics (
    metric_date DATE NOT NULL,
    tenant_id INTEGER NOT NULL,
    branch_count INTEGER NOT NULL DEFAULT 0,
    user_count INTEGER NOT NULL DEFAULT 0,
    client_count INTEGER NOT NULL DEFAULT 0,
    product_count INTEGER NOT NULL DEFAULT 0,
    active_branch_count INTEGER NOT NULL DEFAULT 0,
    locked_branch_count INTEGER NOT NULL DEFAULT 0,
    unpaid_branch_subscriptions INTEGER NOT NULL DEFAULT 0,
    collected_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    sales_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    expense_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (metric_date, tenant_id),
    CONSTRAINT fk_tenant_daily_metrics_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_tenant_daily_metrics_non_negative
        CHECK (
            branch_count >= 0
            AND user_count >= 0
            AND client_count >= 0
            AND product_count >= 0
            AND active_branch_count >= 0
            AND locked_branch_count >= 0
            AND unpaid_branch_subscriptions >= 0
        )
);

COMMENT ON TABLE public.tenant_daily_metrics IS
    'Daily tenant-level read model for platform analytics and overview widgets.';

CREATE INDEX IF NOT EXISTS idx_tenant_daily_metrics_tenant_id
    ON public.tenant_daily_metrics (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_daily_metrics_metric_date
    ON public.tenant_daily_metrics (metric_date DESC);

DROP TRIGGER IF EXISTS trg_tenant_daily_metrics_set_updated_at
ON public.tenant_daily_metrics;

CREATE TRIGGER trg_tenant_daily_metrics_set_updated_at
BEFORE UPDATE ON public.tenant_daily_metrics
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.branch_daily_metrics (
    metric_date DATE NOT NULL,
    tenant_id INTEGER NOT NULL,
    branch_id INTEGER NOT NULL,
    branch_status TEXT NOT NULL DEFAULT 'active',
    active_users_count INTEGER NOT NULL DEFAULT 0,
    client_count INTEGER NOT NULL DEFAULT 0,
    product_count INTEGER NOT NULL DEFAULT 0,
    shift_count INTEGER NOT NULL DEFAULT 0,
    sales_count INTEGER NOT NULL DEFAULT 0,
    sales_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    inventory_adjustment_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (metric_date, branch_id),
    CONSTRAINT fk_branch_daily_metrics_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants (tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_branch_daily_metrics_branch
        FOREIGN KEY (branch_id)
        REFERENCES public."Branch" ("branchId")
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_branch_daily_metrics_branch_status
        CHECK (branch_status IN ('active', 'locked', 'inactive', 'archived')),
    CONSTRAINT chk_branch_daily_metrics_non_negative
        CHECK (
            active_users_count >= 0
            AND client_count >= 0
            AND product_count >= 0
            AND shift_count >= 0
            AND sales_count >= 0
            AND inventory_adjustment_count >= 0
        )
);

COMMENT ON TABLE public.branch_daily_metrics IS
    'Daily branch-level read model for platform operations and drill-down views.';

CREATE INDEX IF NOT EXISTS idx_branch_daily_metrics_tenant_id
    ON public.branch_daily_metrics (tenant_id);

CREATE INDEX IF NOT EXISTS idx_branch_daily_metrics_metric_date
    ON public.branch_daily_metrics (metric_date DESC);

CREATE INDEX IF NOT EXISTS idx_branch_daily_metrics_branch_status
    ON public.branch_daily_metrics (branch_status);

DROP TRIGGER IF EXISTS trg_branch_daily_metrics_set_updated_at
ON public.branch_daily_metrics;

CREATE TRIGGER trg_branch_daily_metrics_set_updated_at
BEFORE UPDATE ON public.branch_daily_metrics
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();
