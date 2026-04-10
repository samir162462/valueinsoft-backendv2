CREATE TABLE IF NOT EXISTS public.business_packages (
    package_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    onboarding_label TEXT NULL,
    business_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    config_version TEXT NOT NULL DEFAULT 'v1',
    description TEXT NOT NULL DEFAULT '',
    default_template_id TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_business_packages_default_template
        FOREIGN KEY (default_template_id)
        REFERENCES public.company_templates (template_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_business_packages_package_id
        CHECK (package_id ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_business_packages_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_business_packages_business_type
        CHECK (length(btrim(business_type)) > 0),
    CONSTRAINT chk_business_packages_status
        CHECK (status IN ('active', 'retired')),
    CONSTRAINT chk_business_packages_config_version
        CHECK (length(btrim(config_version)) > 0),
    CONSTRAINT chk_business_packages_description
        CHECK (length(btrim(description)) >= 0),
    CONSTRAINT chk_business_packages_display_order
        CHECK (display_order >= 0)
);

COMMENT ON TABLE public.business_packages IS
    'Platform-owned operational business packages such as mobile shop or car workshop. Separate from commercial package_plans.';

DROP TRIGGER IF EXISTS trg_business_packages_set_updated_at
ON public.business_packages;

CREATE TRIGGER trg_business_packages_set_updated_at
BEFORE UPDATE ON public.business_packages
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.business_package_groups (
    group_id BIGSERIAL PRIMARY KEY,
    package_id TEXT NOT NULL,
    group_key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_business_package_groups_package
        FOREIGN KEY (package_id)
        REFERENCES public.business_packages (package_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_business_package_groups
        UNIQUE (package_id, group_key),
    CONSTRAINT chk_business_package_groups_group_key
        CHECK (group_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_business_package_groups_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_business_package_groups_status
        CHECK (status IN ('active', 'retired')),
    CONSTRAINT chk_business_package_groups_display_order
        CHECK (display_order >= 0)
);

DROP TRIGGER IF EXISTS trg_business_package_groups_set_updated_at
ON public.business_package_groups;

CREATE TRIGGER trg_business_package_groups_set_updated_at
BEFORE UPDATE ON public.business_package_groups
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.business_package_categories (
    category_id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    category_key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_business_package_categories_group
        FOREIGN KEY (group_id)
        REFERENCES public.business_package_groups (group_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_business_package_categories
        UNIQUE (group_id, category_key),
    CONSTRAINT chk_business_package_categories_category_key
        CHECK (category_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_business_package_categories_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_business_package_categories_status
        CHECK (status IN ('active', 'retired')),
    CONSTRAINT chk_business_package_categories_display_order
        CHECK (display_order >= 0)
);

DROP TRIGGER IF EXISTS trg_business_package_categories_set_updated_at
ON public.business_package_categories;

CREATE TRIGGER trg_business_package_categories_set_updated_at
BEFORE UPDATE ON public.business_package_categories
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

CREATE TABLE IF NOT EXISTS public.business_package_subcategories (
    subcategory_id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    subcategory_key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_business_package_subcategories_category
        FOREIGN KEY (category_id)
        REFERENCES public.business_package_categories (category_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT uq_business_package_subcategories
        UNIQUE (category_id, subcategory_key),
    CONSTRAINT chk_business_package_subcategories_key
        CHECK (subcategory_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_business_package_subcategories_display_name
        CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_business_package_subcategories_status
        CHECK (status IN ('active', 'retired')),
    CONSTRAINT chk_business_package_subcategories_display_order
        CHECK (display_order >= 0)
);

DROP TRIGGER IF EXISTS trg_business_package_subcategories_set_updated_at
ON public.business_package_subcategories;

CREATE TRIGGER trg_business_package_subcategories_set_updated_at
BEFORE UPDATE ON public.business_package_subcategories
FOR EACH ROW
EXECUTE PROCEDURE valueinsoft_set_updated_at();

ALTER TABLE public.tenants
    ADD COLUMN IF NOT EXISTS business_package_id TEXT NULL;

ALTER TABLE public.tenants
    DROP CONSTRAINT IF EXISTS fk_tenants_business_package;

ALTER TABLE public.tenants
    ADD CONSTRAINT fk_tenants_business_package
        FOREIGN KEY (business_package_id)
        REFERENCES public.business_packages (package_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_tenants_business_package_id
    ON public.tenants (business_package_id);
