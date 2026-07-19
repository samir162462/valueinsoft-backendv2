-- Payroll accruals must debit an expense and credit a liability.  Older
-- tenants could accidentally select an expense account for both fields,
-- which netted salary costs to zero in profit-and-loss reporting.
--
-- Every provisioned chart receives account 2500, Salaries Payable. Existing
-- invalid payroll settings/profile overrides are moved to it. Posted journals
-- are deliberately not changed: accounting corrections require an auditable
-- reversal and reposting decision, not a data rewrite.

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
SELECT liabilities.company_id,
       '2500',
       'Salaries Payable',
       'liability',
       'credit',
       liabilities.account_id,
       liabilities.account_path || '.2500',
       liabilities.account_level + 1,
       TRUE,
       FALSE,
       'active',
       COALESCE(liabilities.currency_code, 'EGP'),
       TRUE,
       FALSE,
       FALSE,
       FALSE,
       FALSE
FROM public.finance_account liabilities
WHERE liabilities.account_code = '2000'
  AND liabilities.account_type = 'liability'
  AND liabilities.status = 'active'
  AND NOT EXISTS (
      SELECT 1
      FROM public.finance_account existing
      WHERE existing.company_id = liabilities.company_id
        AND existing.account_code = '2500'
  );

DO $$
DECLARE
    tenant_schema TEXT;
    tenant_id INTEGER;
BEGIN
    FOR tenant_schema IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name ~ '^c_[0-9]+$'
        ORDER BY schema_name
    LOOP
        tenant_id := substring(tenant_schema FROM 3)::INTEGER;

        IF to_regclass(format('%I.payroll_settings', tenant_schema)) IS NOT NULL THEN
            -- Correct only absent, inactive, non-postable, or wrong-class
            -- configuration. A valid company-specific payable remains intact.
            EXECUTE format($sql$
                UPDATE %I.payroll_settings settings
                SET salary_payable_account_id = salaries.account_id
                FROM public.finance_account salaries
                WHERE salaries.company_id = %s
                  AND salaries.account_code = '2500'
                  AND salaries.account_type = 'liability'
                  AND salaries.normal_balance = 'credit'
                  AND salaries.is_postable = TRUE
                  AND salaries.status = 'active'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.finance_account current_account
                      WHERE current_account.company_id = settings.company_id
                        AND current_account.account_id = settings.salary_payable_account_id
                        AND current_account.account_type = 'liability'
                        AND current_account.normal_balance = 'credit'
                        AND current_account.is_postable = TRUE
                        AND current_account.status = 'active'
                  )
            $sql$, tenant_schema, tenant_id);
        END IF;

        IF to_regclass(format('%I.payroll_salary_profile', tenant_schema)) IS NOT NULL THEN
            -- A profile override has precedence over company settings, so a
            -- bad override must be corrected as well.
            EXECUTE format($sql$
                UPDATE %I.payroll_salary_profile profile
                SET salary_payable_account_id = salaries.account_id
                FROM public.finance_account salaries
                WHERE salaries.company_id = %s
                  AND salaries.account_code = '2500'
                  AND salaries.account_type = 'liability'
                  AND salaries.normal_balance = 'credit'
                  AND salaries.is_postable = TRUE
                  AND salaries.status = 'active'
                  AND profile.salary_payable_account_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.finance_account current_account
                      WHERE current_account.company_id = profile.company_id
                        AND current_account.account_id = profile.salary_payable_account_id
                        AND current_account.account_type = 'liability'
                        AND current_account.normal_balance = 'credit'
                        AND current_account.is_postable = TRUE
                        AND current_account.status = 'active'
                  )
            $sql$, tenant_schema, tenant_id);
        END IF;
    END LOOP;
END $$;
