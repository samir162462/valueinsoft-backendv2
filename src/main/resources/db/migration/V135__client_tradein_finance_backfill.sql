-- V135__client_tradein_finance_backfill.sql
--
-- Finance foundation for client trade-in payables.
--
-- Adds, for every tenant that already has a provisioned chart of accounts
-- (identified by the '2000' Liabilities header), a dedicated postable control
-- account:
--     2110 "Client Trade-In Payables" (liability, credit, requires_customer)
-- under the non-postable Accounts Payable structure, plus the company-wide
-- mapping 'purchase.client_payable' -> 2110.
--
-- Newly provisioned tenants receive the same account and mapping from
-- FinanceDefaultAccountsService (seed added in the same change set).
-- Mirrors the V116 / V130 backfill patterns. Idempotent.

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
SELECT parent.company_id,
       '2110',
       'Client Trade-In Payables',
       'liability',
       'credit',
       parent.account_id,
       COALESCE(parent.account_path || '.', '') || '2110',
       COALESCE(parent.account_level + 1, 0),
       TRUE,
       FALSE,
       'active',
       'EGP',
       TRUE,
       TRUE,
       FALSE,
       FALSE,
       FALSE
FROM public.finance_account parent
WHERE parent.account_code = '2000'
  AND parent.status = 'active'
  AND NOT EXISTS (
      SELECT 1
      FROM public.finance_account existing
      WHERE existing.company_id = parent.company_id
        AND existing.account_code = '2110'
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
       'purchase.client_payable',
       fa.account_id,
       100,
       DATE '2020-01-01',
       'active'
FROM public.finance_account fa
WHERE fa.account_code = '2110'
  AND fa.status = 'active'
  AND fa.is_postable = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM public.finance_account_mapping existing
      WHERE existing.company_id = fa.company_id
        AND existing.mapping_key = 'purchase.client_payable'
        AND existing.status = 'active'
        AND existing.branch_id IS NULL
        AND existing.supplier_id IS NULL
  );
