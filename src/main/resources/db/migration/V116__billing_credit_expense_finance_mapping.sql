INSERT INTO public.finance_account (
    company_id,
    account_code,
    account_name,
    account_type,
    normal_balance,
    parent_account_id,
    account_path,
    account_level,
    is_postable,
    is_system,
    status,
    currency_code,
    requires_branch,
    requires_customer,
    requires_supplier,
    requires_product,
    requires_cost_center
)
SELECT c.id,
       '6600',
       'Billing Credits Expense',
       'expense',
       'debit',
       parent.account_id,
       COALESCE(parent.account_path || '.', '') || '6600',
       COALESCE(parent.account_level + 1, 0),
       TRUE,
       FALSE,
       'active',
       'EGP',
       TRUE,
       FALSE,
       FALSE,
       FALSE,
       FALSE
FROM public."Company" c
LEFT JOIN public.finance_account parent
       ON parent.company_id = c.id
      AND parent.account_code = '6000'
      AND parent.status = 'active'
WHERE NOT EXISTS (
    SELECT 1
    FROM public.finance_account existing
    WHERE existing.company_id = c.id
      AND existing.account_code = '6600'
);

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
       'payment.billing_credit_expense',
       fa.account_id,
       100,
       CURRENT_DATE,
       'active'
FROM public.finance_account fa
WHERE fa.account_code = '6600'
  AND fa.status = 'active'
  AND NOT EXISTS (
      SELECT 1
      FROM public.finance_account_mapping existing
      WHERE existing.company_id = fa.company_id
        AND existing.branch_id IS NULL
        AND existing.supplier_id IS NULL
        AND existing.mapping_key = 'payment.billing_credit_expense'
        AND existing.status = 'active'
  );
