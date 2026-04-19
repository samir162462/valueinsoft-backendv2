CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.inventory_presets (
    preset_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id INTEGER NOT NULL,
    branch_id INTEGER,
    preset_name VARCHAR(100) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    role_target VARCHAR(50),
    query_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_presets_company_fk FOREIGN KEY (company_id) REFERENCES public."Company" (id) ON DELETE CASCADE,
    CONSTRAINT inventory_presets_branch_fk FOREIGN KEY (branch_id) REFERENCES public."Branch" ("branchId") ON DELETE CASCADE,
    CONSTRAINT inventory_presets_scope_ck CHECK (scope IN ('global', 'company', 'branch')),
    CONSTRAINT inventory_presets_mode_ck CHECK (mode IN ('browse', 'analysis', 'quickFind'))
);

CREATE INDEX IF NOT EXISTS idx_inventory_presets_company_branch ON public.inventory_presets (company_id, branch_id);
