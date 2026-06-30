-- This script generates a list of orphan products (products with no branch assortment) across all company schemas
-- and outputs them. Run this script via psql or PgAdmin to view the report.

DO $$
DECLARE
    schema_record RECORD;
    query text;
BEGIN
    -- Create a temporary table to hold orphan products report
    CREATE TEMP TABLE IF NOT EXISTS orphan_products_report (
        company_id int,
        product_id bigint,
        product_name varchar,
        supplier_id int,
        reason varchar
    ) ON COMMIT DROP;

    FOR schema_record IN 
        SELECT schema_name 
        FROM information_schema.schemata 
        WHERE schema_name ~ '^c_[0-9]+$'
    LOOP
        query := format('
            INSERT INTO orphan_products_report (company_id, product_id, product_name, supplier_id, reason)
            SELECT 
                CAST(substring(%I FROM 3) AS INT),
                p.product_id,
                p.product_name,
                p.supplier_id,
                ''No active branch assignment''
            FROM %I.inventory_product p
            LEFT JOIN %I.inventory_branch_product ibp ON ibp.product_id = p.product_id
            WHERE ibp.product_id IS NULL;
        ', schema_record.schema_name, schema_record.schema_name, schema_record.schema_name);
        
        EXECUTE query;
    END LOOP;
END $$;

-- Select the output so it can be exported to CSV by the client (e.g. pgAdmin or psql \copy)
SELECT * FROM orphan_products_report ORDER BY company_id, product_id;
