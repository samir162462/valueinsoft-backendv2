DO $$
DECLARE
    company_record RECORD;
    target_schema_name TEXT;
BEGIN
    FOR company_record IN SELECT id FROM public."Company" ORDER BY id LOOP
        target_schema_name := format('c_%s', company_record.id);

        IF to_regclass(format('%I.inventory_branch_product', target_schema_name)) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS group_key VARCHAR(80)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS category_key VARCHAR(80)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS subcategory_key VARCHAR(80)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS group_name VARCHAR(100)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS category_name VARCHAR(100)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS subcategory_name VARCHAR(100)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS brand VARCHAR(100)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS model VARCHAR(100)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS manufacturer VARCHAR(100)', target_schema_name);
        EXECUTE format('ALTER TABLE %I.inventory_branch_product ADD COLUMN IF NOT EXISTS taxonomy_version INTEGER', target_schema_name);

        IF to_regclass(format('%I.inventory_product', target_schema_name)) IS NOT NULL THEN
            EXECUTE format('
                UPDATE %I.inventory_branch_product ibp
                SET category_name = COALESCE(ibp.category_name, NULLIF(p.major, '''')),
                    subcategory_name = COALESCE(ibp.subcategory_name, NULLIF(p.product_type, '''')),
                    taxonomy_version = COALESCE(ibp.taxonomy_version, 0)
                FROM %I.inventory_product p
                WHERE p.product_id = ibp.product_id
            ', target_schema_name, target_schema_name);
        END IF;

        IF to_regclass(format('%I."PosCateJson"', target_schema_name)) IS NOT NULL
           AND to_regclass(format('%I.inventory_product', target_schema_name)) IS NOT NULL THEN
            EXECUTE format($sql$
                WITH latest_category AS (
                    SELECT DISTINCT ON ("BranchId")
                        "BranchId" AS branch_id,
                        "CategoryData"::jsonb AS payload
                    FROM %I."PosCateJson"
                    ORDER BY "BranchId", "CategoryJID" DESC
                ),
                taxonomy_roots AS (
                    SELECT
                        branch_id,
                        CASE
                            WHEN jsonb_typeof(payload->'groups') = 'array' THEN payload->'groups'
                            WHEN jsonb_typeof(payload->'categoryData'->'groups') = 'array' THEN payload->'categoryData'->'groups'
                            ELSE '[]'::jsonb
                        END AS groups
                    FROM latest_category
                ),
                taxonomy_paths AS (
                    SELECT
                        root.branch_id,
                        group_node->>'key' AS group_key,
                        group_node->>'label' AS group_name,
                        category_node->>'key' AS category_key,
                        category_node->>'label' AS category_name,
                        subcategory_node->>'key' AS subcategory_key,
                        subcategory_node->>'label' AS subcategory_name
                    FROM taxonomy_roots root
                    CROSS JOIN LATERAL jsonb_array_elements(root.groups) group_node
                    CROSS JOIN LATERAL jsonb_array_elements(
                        CASE WHEN jsonb_typeof(group_node->'categories') = 'array' THEN group_node->'categories' ELSE '[]'::jsonb END
                    ) category_node
                    CROSS JOIN LATERAL jsonb_array_elements(
                        CASE WHEN jsonb_typeof(category_node->'subcategories') = 'array' THEN category_node->'subcategories' ELSE '[]'::jsonb END
                    ) subcategory_node
                    WHERE group_node ? 'key'
                      AND category_node ? 'key'
                      AND subcategory_node ? 'key'
                ),
                unambiguous_matches AS (
                    SELECT
                        ibp.branch_id,
                        ibp.product_id,
                        MIN(path.group_key) AS group_key,
                        MIN(path.group_name) AS group_name,
                        MIN(path.category_key) AS category_key,
                        MIN(path.category_name) AS category_name,
                        MIN(path.subcategory_key) AS subcategory_key,
                        MIN(path.subcategory_name) AS subcategory_name,
                        COUNT(*) AS match_count
                    FROM %I.inventory_branch_product ibp
                    JOIN %I.inventory_product p ON p.product_id = ibp.product_id
                    JOIN taxonomy_paths path ON path.branch_id = ibp.branch_id
                    WHERE NULLIF(p.major, '') IS NOT NULL
                      AND NULLIF(p.product_type, '') IS NOT NULL
                      AND lower(path.category_name) = lower(p.major)
                      AND lower(path.subcategory_name) = lower(p.product_type)
                    GROUP BY ibp.branch_id, ibp.product_id
                )
                UPDATE %I.inventory_branch_product ibp
                SET group_key = match.group_key,
                    category_key = match.category_key,
                    subcategory_key = match.subcategory_key,
                    group_name = COALESCE(ibp.group_name, match.group_name),
                    category_name = COALESCE(ibp.category_name, match.category_name),
                    subcategory_name = COALESCE(ibp.subcategory_name, match.subcategory_name),
                    taxonomy_version = 2
                FROM unambiguous_matches match
                WHERE match.match_count = 1
                  AND match.branch_id = ibp.branch_id
                  AND match.product_id = ibp.product_id
                  AND (ibp.category_key IS NULL OR ibp.subcategory_key IS NULL)
            $sql$, target_schema_name, target_schema_name, target_schema_name, target_schema_name);
        END IF;

        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I.inventory_branch_product (branch_id, group_key, category_key, subcategory_key)',
            'idx_' || target_schema_name || '_ibp_classification', target_schema_name);
    END LOOP;
END $$;
