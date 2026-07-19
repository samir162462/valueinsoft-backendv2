CREATE TABLE public.tenant_access_audit_events (
    event_id BIGSERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    actor_user_id INTEGER NOT NULL,
    target_user_id INTEGER NOT NULL,
    capability_key VARCHAR(120) NOT NULL,
    action VARCHAR(32) NOT NULL,
    grant_mode VARCHAR(20),
    scope_type VARCHAR(20) NOT NULL,
    scope_branch_id INTEGER,
    reason VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_access_audit_scope
        CHECK (scope_type IN ('company', 'branch', 'self')),
    CONSTRAINT chk_tenant_access_audit_grant_mode
        CHECK (grant_mode IS NULL OR grant_mode IN ('allow', 'deny')),
    CONSTRAINT chk_tenant_access_audit_branch_scope
        CHECK ((scope_type = 'branch' AND scope_branch_id IS NOT NULL)
            OR (scope_type <> 'branch' AND scope_branch_id IS NULL))
);

CREATE INDEX idx_tenant_access_audit_tenant_created
    ON public.tenant_access_audit_events (tenant_id, created_at DESC);

CREATE INDEX idx_tenant_access_audit_target_created
    ON public.tenant_access_audit_events (tenant_id, target_user_id, created_at DESC);

