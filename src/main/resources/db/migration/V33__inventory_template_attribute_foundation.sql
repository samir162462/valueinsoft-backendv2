CREATE TABLE IF NOT EXISTS public.inventory_business_line (
    business_line_key VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.inventory_product_template (
    template_id BIGSERIAL PRIMARY KEY,
    business_line_key VARCHAR(40) NOT NULL,
    template_key VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    major_key VARCHAR(40),
    supports_serial BOOLEAN NOT NULL DEFAULT FALSE,
    supports_batch BOOLEAN NOT NULL DEFAULT FALSE,
    supports_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    supports_weight BOOLEAN NOT NULL DEFAULT FALSE,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_product_template_business_line_fk
        FOREIGN KEY (business_line_key) REFERENCES public.inventory_business_line (business_line_key) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.inventory_attribute_definition (
    attribute_id BIGSERIAL PRIMARY KEY,
    business_line_key VARCHAR(40) NOT NULL,
    attribute_key VARCHAR(80) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_filterable BOOLEAN NOT NULL DEFAULT FALSE,
    is_searchable BOOLEAN NOT NULL DEFAULT FALSE,
    field_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_attribute_definition_business_line_fk
        FOREIGN KEY (business_line_key) REFERENCES public.inventory_business_line (business_line_key) ON DELETE CASCADE,
    CONSTRAINT inventory_attribute_definition_data_type_ck
        CHECK (data_type IN ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'JSON')),
    CONSTRAINT inventory_attribute_definition_unique_key
        UNIQUE (business_line_key, attribute_key)
);

CREATE TABLE IF NOT EXISTS public.inventory_template_attribute (
    template_id BIGINT NOT NULL,
    attribute_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    group_key VARCHAR(80),
    default_value_jsonb JSONB,
    PRIMARY KEY (template_id, attribute_id),
    CONSTRAINT inventory_template_attribute_template_fk
        FOREIGN KEY (template_id) REFERENCES public.inventory_product_template (template_id) ON DELETE CASCADE,
    CONSTRAINT inventory_template_attribute_attribute_fk
        FOREIGN KEY (attribute_id) REFERENCES public.inventory_attribute_definition (attribute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.inventory_product_attribute_value (
    product_id BIGINT NOT NULL,
    attribute_id BIGINT NOT NULL,
    value_text TEXT,
    value_number DOUBLE PRECISION,
    value_boolean BOOLEAN,
    value_date DATE,
    value_jsonb JSONB,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, attribute_id),
    CONSTRAINT inventory_product_attribute_value_product_fk
        FOREIGN KEY (product_id) REFERENCES public.inventory_product (product_id) ON DELETE CASCADE,
    CONSTRAINT inventory_product_attribute_value_attribute_fk
        FOREIGN KEY (attribute_id) REFERENCES public.inventory_attribute_definition (attribute_id) ON DELETE CASCADE
);

ALTER TABLE public.inventory_product
    ADD COLUMN IF NOT EXISTS business_line_key VARCHAR(40) NOT NULL DEFAULT 'MOBILE',
    ADD COLUMN IF NOT EXISTS template_key VARCHAR(80) NOT NULL DEFAULT 'mobile_device';

INSERT INTO public.inventory_business_line (business_line_key, display_name)
VALUES
    ('MOBILE', 'Mobile Shop'),
    ('GOLD', 'Gold Shop'),
    ('CHEMICAL', 'Chemical Shop')
ON CONFLICT (business_line_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.inventory_product_template (
    business_line_key, template_key, display_name, major_key,
    supports_serial, supports_batch, supports_expiry, supports_weight
)
VALUES
    ('MOBILE', 'mobile_device', 'Mobile Device', 'Mobiles', TRUE, FALSE, FALSE, FALSE),
    ('MOBILE', 'mobile_accessory', 'Mobile Accessory', 'Accessories', FALSE, FALSE, FALSE, FALSE),
    ('GOLD', 'gold_item', 'Gold Item', 'Gold', FALSE, FALSE, FALSE, TRUE),
    ('CHEMICAL', 'chemical_item', 'Chemical Item', 'Chemicals', FALSE, TRUE, TRUE, FALSE)
ON CONFLICT (template_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    major_key = EXCLUDED.major_key,
    supports_serial = EXCLUDED.supports_serial,
    supports_batch = EXCLUDED.supports_batch,
    supports_expiry = EXCLUDED.supports_expiry,
    supports_weight = EXCLUDED.supports_weight,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.inventory_attribute_definition (
    business_line_key, attribute_key, display_name, data_type, is_required, is_filterable, is_searchable, field_schema
)
VALUES
    ('MOBILE', 'manufacturer', 'Manufacturer', 'TEXT', FALSE, TRUE, TRUE, '{"control":"text"}'),
    ('MOBILE', 'model', 'Model', 'TEXT', FALSE, TRUE, TRUE, '{"control":"text"}'),
    ('MOBILE', 'storage', 'Storage', 'TEXT', FALSE, TRUE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'ram', 'RAM', 'TEXT', FALSE, TRUE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'cpu', 'CPU', 'TEXT', FALSE, FALSE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'screenSize', 'Screen Size', 'TEXT', FALSE, FALSE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'batteryCapacity', 'Battery Capacity', 'TEXT', FALSE, FALSE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'camera', 'Camera', 'TEXT', FALSE, FALSE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'dualSim', 'Dual SIM', 'BOOLEAN', FALSE, TRUE, FALSE, '{"control":"switch"}'),
    ('MOBILE', 'warranty', 'Warranty', 'TEXT', FALSE, FALSE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'network', 'Network', 'TEXT', FALSE, TRUE, FALSE, '{"control":"text"}'),
    ('MOBILE', 'imei', 'IMEI', 'TEXT', FALSE, TRUE, TRUE, '{"control":"text"}'),
    ('MOBILE', 'goMarket', 'Go Market', 'BOOLEAN', FALSE, TRUE, FALSE, '{"control":"switch"}')
ON CONFLICT (business_line_key, attribute_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    data_type = EXCLUDED.data_type,
    is_required = EXCLUDED.is_required,
    is_filterable = EXCLUDED.is_filterable,
    is_searchable = EXCLUDED.is_searchable,
    field_schema = EXCLUDED.field_schema,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.inventory_template_attribute (
    template_id, attribute_id, display_order, is_required, group_key, default_value_jsonb
)
SELECT template.template_id,
       attribute.attribute_id,
       definition.display_order,
       definition.is_required,
       definition.group_key,
       definition.default_value_jsonb
FROM (
    VALUES
        ('mobile_device', 'manufacturer', 10, FALSE, 'identity', null::jsonb),
        ('mobile_device', 'model', 20, FALSE, 'identity', null::jsonb),
        ('mobile_device', 'storage', 30, FALSE, 'hardware', null::jsonb),
        ('mobile_device', 'ram', 40, FALSE, 'hardware', null::jsonb),
        ('mobile_device', 'cpu', 50, FALSE, 'hardware', null::jsonb),
        ('mobile_device', 'screenSize', 60, FALSE, 'hardware', null::jsonb),
        ('mobile_device', 'batteryCapacity', 70, FALSE, 'hardware', null::jsonb),
        ('mobile_device', 'camera', 80, FALSE, 'hardware', null::jsonb),
        ('mobile_device', 'dualSim', 90, FALSE, 'connectivity', 'false'::jsonb),
        ('mobile_device', 'warranty', 100, FALSE, 'commercial', null::jsonb),
        ('mobile_device', 'network', 110, FALSE, 'connectivity', null::jsonb),
        ('mobile_device', 'imei', 120, FALSE, 'identity', null::jsonb),
        ('mobile_device', 'goMarket', 130, FALSE, 'commercial', 'false'::jsonb)
) AS definition(template_key, attribute_key, display_order, is_required, group_key, default_value_jsonb)
JOIN public.inventory_product_template template
  ON template.template_key = definition.template_key
JOIN public.inventory_attribute_definition attribute
  ON attribute.business_line_key = template.business_line_key
 AND attribute.attribute_key = definition.attribute_key
ON CONFLICT (template_id, attribute_id) DO UPDATE
SET display_order = EXCLUDED.display_order,
    is_required = EXCLUDED.is_required,
    group_key = EXCLUDED.group_key,
    default_value_jsonb = EXCLUDED.default_value_jsonb;

CREATE INDEX IF NOT EXISTS idx_inventory_product_business_line_template
    ON public.inventory_product (company_id, business_line_key, template_key);

CREATE INDEX IF NOT EXISTS idx_inventory_product_attribute_value_attribute_text
    ON public.inventory_product_attribute_value (attribute_id, value_text);

CREATE INDEX IF NOT EXISTS idx_inventory_product_attribute_value_attribute_number
    ON public.inventory_product_attribute_value (attribute_id, value_number);

CREATE INDEX IF NOT EXISTS idx_inventory_product_attribute_value_attribute_boolean
    ON public.inventory_product_attribute_value (attribute_id, value_boolean);
