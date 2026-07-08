CREATE OR REPLACE FUNCTION public.vls_is_valid_imei(value text)
RETURNS boolean
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    normalized text := btrim(value);
    checksum int := 0;
    position_from_right int := 0;
    digit int;
BEGIN
    IF normalized IS NULL OR normalized !~ '^[0-9]{15}$' THEN
        RETURN false;
    END IF;

    FOR idx IN REVERSE 15..1 LOOP
        digit := substr(normalized, idx, 1)::int;
        IF position_from_right % 2 = 1 THEN
            digit := digit * 2;
            IF digit > 9 THEN
                digit := digit - 9;
            END IF;
        END IF;

        checksum := checksum + digit;
        position_from_right := position_from_right + 1;
    END LOOP;

    RETURN checksum % 10 = 0;
END;
$$;

CREATE OR REPLACE FUNCTION public.ensure_serialized_inventory_imei_constraints_for_tenant(schema_name text)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF to_regclass(format('%I.%I', schema_name, 'inventory_product_unit')) IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = schema_name::regnamespace
          AND conrelid = format('%I.%I', schema_name, 'inventory_product_unit')::regclass
          AND conname = 'inventory_product_unit_imei_luhn_ck'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_imei_luhn_ck
                CHECK (tracking_type <> ''IMEI'' OR public.vls_is_valid_imei(imei))
                NOT VALID',
            schema_name
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE connamespace = schema_name::regnamespace
          AND conrelid = format('%I.%I', schema_name, 'inventory_product_unit')::regclass
          AND conname = 'inventory_product_unit_canonical_identifier_ck'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_product_unit
                ADD CONSTRAINT inventory_product_unit_canonical_identifier_ck
                CHECK (
                    (tracking_type = ''IMEI'' AND lower(unit_identifier) = lower(imei))
                    OR
                    (tracking_type = ''SERIAL'' AND lower(unit_identifier) = lower(serial_number))
                )
                NOT VALID',
            schema_name
        );
    END IF;
END;
$$;

DO $$
DECLARE
    schema_rec record;
BEGIN
    FOR schema_rec IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'c\_%' ESCAPE '\'
    LOOP
        PERFORM public.ensure_serialized_inventory_imei_constraints_for_tenant(schema_rec.schema_name);
    END LOOP;
END;
$$;
