DO $$
DECLARE
    schema_record RECORD;
BEGIN
    IF to_regclass('public.inventory_product') IS NOT NULL THEN
        ALTER TABLE public.inventory_product
            ADD COLUMN IF NOT EXISTS business_line_key VARCHAR(40) NOT NULL DEFAULT 'MOBILE',
            ADD COLUMN IF NOT EXISTS template_key VARCHAR(80) NOT NULL DEFAULT 'mobile_device';

        UPDATE public.inventory_product
        SET business_line_key = 'MOBILE'
        WHERE business_line_key IS NULL OR btrim(business_line_key) = '';

        UPDATE public.inventory_product
        SET template_key = 'mobile_device'
        WHERE template_key IS NULL OR btrim(template_key) = '';
    END IF;

    FOR schema_record IN
        SELECT nspname AS schema_name
        FROM pg_namespace
        WHERE nspname ~ '^c_[0-9]+$'
        ORDER BY nspname
    LOOP
        IF to_regclass(format('%I.inventory_product', schema_record.schema_name)) IS NOT NULL THEN
            EXECUTE format('
                ALTER TABLE %I.inventory_product
                    ADD COLUMN IF NOT EXISTS business_line_key VARCHAR(40) NOT NULL DEFAULT ''MOBILE'',
                    ADD COLUMN IF NOT EXISTS template_key VARCHAR(80) NOT NULL DEFAULT ''mobile_device''
            ', schema_record.schema_name);

            EXECUTE format('
                UPDATE %I.inventory_product
                SET business_line_key = ''MOBILE''
                WHERE business_line_key IS NULL OR btrim(business_line_key) = ''''
            ', schema_record.schema_name);

            EXECUTE format('
                UPDATE %I.inventory_product
                SET template_key = ''mobile_device''
                WHERE template_key IS NULL OR btrim(template_key) = ''''
            ', schema_record.schema_name);

            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS %I ON %I.inventory_product (business_line_key, template_key, product_id)',
                'idx_' || schema_record.schema_name || '_inventory_product_business_template',
                schema_record.schema_name
            );
        END IF;
    END LOOP;
END $$;
