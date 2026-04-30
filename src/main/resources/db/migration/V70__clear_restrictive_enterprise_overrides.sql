-- V70__clear_restrictive_enterprise_overrides.sql

-- 1. Identify all tenants on the 'enterprise' plan
-- 2. Delete or update any module overrides that are restricting access (mode = 'hidden' or 'read_only')
-- These were likely artifacts from legacy migrations and are blocking 'full access'

-- Clear 'hidden' and 'read_only' overrides for Enterprise tenants to restore plan defaults
DELETE FROM public.tenant_module_overrides tmo
USING public.tenants tc
WHERE tmo.tenant_id = tc.tenant_id
  AND tc.package_id = 'enterprise'
  AND (tmo.mode = 'hidden' OR tmo.mode = 'read_only' OR tmo.enabled = FALSE);

-- Ensure that modules like dashboard, finance, and inventory are not inadvertently blocked
-- for existing enterprise tenants who might have stale state
UPDATE public.tenant_module_overrides tmo
SET mode = 'custom', enabled = TRUE
FROM public.tenants tc
WHERE tmo.tenant_id = tc.tenant_id
  AND tc.package_id = 'enterprise'
  AND tmo.module_id IN ('dashboard', 'finance', 'inventory', 'suppliers_management')
  AND (tmo.mode IS NULL OR tmo.enabled = FALSE);
