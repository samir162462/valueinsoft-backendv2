-- Backfill default finance account mappings for existing tenants.
--
-- Tenants provisioned before the payment.* mapping seeds were added to
-- FinanceDefaultAccountsService are missing entries such as
-- 'payment.cash_drawer'. Supplier and POS payment postings then fail with
-- "Missing active finance account mapping: payment.cash_drawer".
--
-- This inserts any missing default mapping as a company-wide (branch-agnostic,
-- supplier-agnostic) active mapping so resolution succeeds for every branch.
-- It is idempotent: a mapping is only created when no active company-wide entry
-- for that key already exists, and only when the target default account exists.
-- New branches continue to receive branch-scoped mappings from the seeder.
-- Mirrors the mapping seeds in FinanceDefaultAccountsService and the backfill
-- pattern used by V114 / V116.

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
       d.mapping_key,
       fa.account_id,
       100,
       DATE '2020-01-01',
       'active'
FROM (
    VALUES
        -- POS
        ('pos.sales',                    '4100'),
        ('pos.cash',                     '1011'),
        ('pos.card',                     '1031'),
        ('pos.wallet',                   '1032'),
        ('pos.receivable',               '1100'),
        ('pos.discount',                 '4400'),
        ('pos.output_vat',               '2200'),
        ('pos.cogs',                     '5000'),
        ('pos.inventory',                '1200'),
        ('pos.sales_returns',            '4300'),
        -- Purchase
        ('purchase.inventory',           '1200'),
        ('purchase.input_vat',           '1300'),
        ('purchase.payable',             '2100'),
        ('purchase.grni',                '2400'),
        ('purchase.cash',                '1011'),
        ('purchase.bank',                '1021'),
        ('purchase.card',                '1031'),
        ('purchase.wallet',              '1032'),
        -- Inventory
        ('inventory.asset',              '1200'),
        ('inventory.damage_expense',     '6500'),
        ('inventory.writeoff_expense',   '6510'),
        -- Payment
        ('payment.cash',                 '1011'),
        ('payment.card',                 '1031'),
        ('payment.wallet',               '1032'),
        ('payment.bank',                 '1021'),
        ('payment.cash_drawer',          '1011'),
        ('payment.cash_safe',            '1012'),
        ('payment.card_clearing',        '1031'),
        ('payment.customer_deposits',    '2300'),
        ('payment.billing_credit_expense','6600'),
        ('payment.fee_expense',          '6400')
) AS d(mapping_key, account_code)
JOIN public.finance_account fa
      ON fa.account_code = d.account_code
     AND fa.status = 'active'
     AND fa.is_postable = TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM public.finance_account_mapping existing
    WHERE existing.company_id = fa.company_id
      AND existing.mapping_key = d.mapping_key
      AND existing.status = 'active'
      AND existing.branch_id IS NULL
      AND existing.supplier_id IS NULL
);
