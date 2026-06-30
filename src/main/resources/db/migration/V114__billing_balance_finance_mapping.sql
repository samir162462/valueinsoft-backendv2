INSERT INTO public.finance_account_mapping (
    company_id,
    branch_id,
    supplier_id,
    mapping_key,
    account_id,
    priority,
    effective_from,
    status
)
SELECT fa.company_id,
       NULL,
       NULL,
       'payment.customer_deposits',
       fa.account_id,
       100,
       CURRENT_DATE,
       'active'
FROM public.finance_account fa
WHERE fa.account_code = '2300'
  AND fa.status = 'active'
  AND NOT EXISTS (
      SELECT 1
      FROM public.finance_account_mapping existing
      WHERE existing.company_id = fa.company_id
        AND existing.branch_id IS NULL
        AND existing.supplier_id IS NULL
        AND existing.mapping_key = 'payment.customer_deposits'
        AND existing.status = 'active'
  );
