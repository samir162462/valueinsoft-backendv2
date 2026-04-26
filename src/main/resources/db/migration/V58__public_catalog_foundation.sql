-- V58__public_catalog_foundation.sql
-- Phase 1: Database Foundation for Public Tenant Product Catalog

-- 1. Create public metadata table for tenants
CREATE TABLE IF NOT EXISTS public.public_tenants (
    tenant_id INTEGER PRIMARY KEY,
    tenant_code VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    logo_url TEXT,
    primary_color VARCHAR(20) DEFAULT '#007bff',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_public_tenants_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(tenant_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_public_tenants_code ON public.public_tenants(tenant_code);

-- 2. Add online visibility fields to each tenant's inventory_product table
DO $$
DECLARE
    company_record RECORD;
    v_schema_name TEXT;
BEGIN
    FOR company_record IN
        SELECT id
        FROM public."Company"
        ORDER BY id
    LOOP
        v_schema_name := format('c_%s', company_record.id);

        -- Check if schema exists before attempting to alter
        IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = v_schema_name) THEN
            
            -- Add columns if they don't exist
            EXECUTE format('ALTER TABLE %I.inventory_product ADD COLUMN IF NOT EXISTS show_online BOOLEAN DEFAULT FALSE', v_schema_name);
            EXECUTE format('ALTER TABLE %I.inventory_product ADD COLUMN IF NOT EXISTS online_description TEXT', v_schema_name);
            EXECUTE format('ALTER TABLE %I.inventory_product ADD COLUMN IF NOT EXISTS online_image_url TEXT', v_schema_name);
            EXECUTE format('ALTER TABLE %I.inventory_product ADD COLUMN IF NOT EXISTS online_offer_price NUMERIC(19,4)', v_schema_name);
            EXECUTE format('ALTER TABLE %I.inventory_product ADD COLUMN IF NOT EXISTS online_sort_order INTEGER DEFAULT 0', v_schema_name);
            EXECUTE format('ALTER TABLE %I.inventory_product ADD COLUMN IF NOT EXISTS online_active BOOLEAN DEFAULT TRUE', v_schema_name);

        END IF;
    END LOOP;
END $$;
