-- Some legacy products stored the tracking type (IMEI / SERIAL) inside
-- inventory_product.base_uom_code. base_uom_code must be a real unit of
-- measure (validated against inventory_uom_unit), so those values broke
-- the bulk product import sample export and validation ("wrong unit code").
--
-- Cleanup per tenant schema:
--   * base_uom_code IMEI/SERIAL -> PCS
--   * if the product was still QUANTITY-tracked, promote tracking_type to
--     the tracking type that was mistakenly stored as the unit code.

DO $$
DECLARE
    company_record RECORD;
    schema_name TEXT;
    product_table REGCLASS;
BEGIN
    FOR company_record IN SELECT id FROM public."Company" WHERE id > 0 LOOP
        schema_name := 'c_' || company_record.id;
        IF to_regnamespace(schema_name) IS NULL THEN
            CONTINUE;
        END IF;

        product_table := to_regclass(format('%I.%I', schema_name, 'inventory_product'));
        IF product_table IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('
            UPDATE %I.inventory_product
            SET tracking_type = CASE
                    WHEN UPPER(TRIM(base_uom_code)) = ''IMEI''
                        AND COALESCE(tracking_type, ''QUANTITY'') = ''QUANTITY'' THEN ''IMEI''
                    WHEN UPPER(TRIM(base_uom_code)) = ''SERIAL''
                        AND COALESCE(tracking_type, ''QUANTITY'') = ''QUANTITY'' THEN ''SERIAL''
                    ELSE tracking_type
                END,
                base_uom_code = ''PCS'',
                updated_at = CURRENT_TIMESTAMP
            WHERE UPPER(TRIM(COALESCE(base_uom_code, ''''))) IN (''IMEI'', ''SERIAL'')
        ', schema_name);
    END LOOP;
END;
$$;
