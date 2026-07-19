-- Inventory history must be derived from real ledger rows only. Older serialized units could
-- exist without an acquisition ledger row, which previously forced the read query to invent
-- negative transaction ids and caused modern receipt rows to be counted twice.

CREATE OR REPLACE FUNCTION public.backfill_serialized_inventory_history_for_tenant(
    target_schema TEXT,
    target_company_id INTEGER
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    inserted_count BIGINT := 0;
BEGIN
    IF target_schema IS NULL OR btrim(target_schema) = '' THEN
        RAISE EXCEPTION 'target_schema is required';
    END IF;

    IF target_company_id IS NULL OR target_company_id <= 0 THEN
        RAISE EXCEPTION 'target_company_id must be positive';
    END IF;

    IF to_regclass(format('%I.%I', target_schema, 'inventory_product_unit')) IS NULL
       OR to_regclass(format('%I.%I', target_schema, 'inventory_product')) IS NULL
       OR to_regclass(format('%I.%I', target_schema, 'inventory_stock_ledger')) IS NULL THEN
        RETURN 0;
    END IF;

    EXECUTE format($sql$
        WITH inserted AS (
            INSERT INTO %I.inventory_stock_ledger (
                company_id,
                branch_id,
                product_id,
                product_unit_id,
                quantity_delta,
                movement_type,
                reference_type,
                reference_id,
                actor_name,
                note,
                supplier_id,
                trans_total,
                pay_type,
                remaining_amount,
                idempotency_key,
                created_at
            )
            SELECT
                $1,
                unit.branch_id,
                unit.product_id,
                unit.product_unit_id,
                1,
                'STOCK_IN',
                COALESCE(NULLIF(unit.purchase_reference_type, ''), 'SERIALIZED_UNIT_BACKFILL'),
                COALESCE(NULLIF(unit.purchase_reference_id, ''), unit.product_unit_id::text),
                'system',
                'Backfilled serialized acquisition history',
                COALESCE(unit.supplier_id, 0),
                CASE
                    WHEN unit.acquisition_cost IS NULL THEN 0
                    WHEN round(unit.acquisition_cost) BETWEEN -2147483648 AND 2147483647
                        THEN round(unit.acquisition_cost)::integer
                    ELSE 0
                END,
                '',
                0,
                'serialized-history-backfill:' || unit.product_unit_id,
                COALESCE(unit.received_at, unit.created_at, CURRENT_TIMESTAMP)
            FROM %I.inventory_product_unit unit
            JOIN %I.inventory_product product ON product.product_id = unit.product_id
            WHERE COALESCE(product.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
              AND NOT EXISTS (
                  SELECT 1
                  FROM %I.inventory_stock_ledger ledger
                  WHERE ledger.branch_id = unit.branch_id
                    AND ledger.product_id = unit.product_id
                    AND ledger.quantity_delta > 0
                    AND ledger.movement_type IN (
                        'PURCHASE_RECEIPT', 'STOCK_IN', 'OPENING_BALANCE', 'MANUAL_STOCK_IN'
                    )
                    AND NOT (
                        ledger.reference_type = 'PRODUCT_CREATE'
                        AND ledger.movement_type = 'OPENING_BALANCE'
                    )
                    AND (
                        ledger.product_unit_id = unit.product_unit_id
                        OR (
                            NULLIF(unit.purchase_reference_type, '') IS NOT NULL
                            AND NULLIF(unit.purchase_reference_id, '') IS NOT NULL
                            AND ledger.reference_type = unit.purchase_reference_type
                            AND ledger.reference_id = unit.purchase_reference_id
                        )
                    )
              )
            ON CONFLICT DO NOTHING
            RETURNING 1
        )
        SELECT count(*) FROM inserted
    $sql$, target_schema, target_schema, target_schema, target_schema)
    INTO inserted_count
    USING target_company_id;

    RETURN inserted_count;
END;
$$;

DO $$
DECLARE
    company_record RECORD;
    tenant_schema TEXT;
BEGIN
    FOR company_record IN
        SELECT id
        FROM public."Company"
        WHERE id > 0
        ORDER BY id
    LOOP
        tenant_schema := format('c_%s', company_record.id);
        IF to_regnamespace(tenant_schema) IS NOT NULL THEN
            PERFORM public.backfill_serialized_inventory_history_for_tenant(
                tenant_schema,
                company_record.id
            );
        END IF;
    END LOOP;
END;
$$;
