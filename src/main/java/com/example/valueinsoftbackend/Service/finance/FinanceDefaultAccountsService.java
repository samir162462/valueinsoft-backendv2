package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountMappingCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Idempotent seeder that auto-creates the default chart of accounts and
 * branch-scoped account mappings when a new branch is provisioned.
 *
 * <p>Accounts are company-level: they are created once for the company and
 * skipped on subsequent branch creations. Mappings are branch-scoped: a
 * full set of mappings is created for every new branch, skipping any that
 * already have an active entry for the same key and branch.
 */
@Service
@Slf4j
public class FinanceDefaultAccountsService {

    // ── Effective date used for all auto-seeded mappings ────────────────────
    private static final LocalDate MAPPING_EFFECTIVE_FROM = LocalDate.of(2020, 1, 1);

    private final DbFinanceSetup dbFinanceSetup;

    public FinanceDefaultAccountsService(DbFinanceSetup dbFinanceSetup) {
        this.dbFinanceSetup = dbFinanceSetup;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Public entry point
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Called by {@link com.example.valueinsoftbackend.Service.branch.BranchService}
     * immediately after a branch is created.
     *
     * <p>Steps:
     * <ol>
     *   <li>Seed all default accounts for the company (skips codes that exist).</li>
     *   <li>Build a code → accountId lookup map.</li>
     *   <li>Seed branch-scoped mappings for the new branch (skips existing ones).</li>
     * </ol>
     *
     * @param companyId the company that owns the new branch
     * @param branchId  the newly created branch
     */
    @Transactional
    public void provisionDefaultAccountsIfMissing(int companyId, int branchId) {
        log.info("Finance default provisioning started – companyId={} branchId={}", companyId, branchId);

        // 1. Seed company-wide accounts
        seedDefaultAccounts(companyId);

        // 2. Resolve current account IDs by code (accounts now guaranteed to exist)
        Map<String, UUID> accountsByCode = buildAccountCodeMap(companyId);

        // 3. Seed branch-scoped mappings
        int mappingsCreated = seedBranchMappings(companyId, branchId, accountsByCode);

        log.info("Finance default provisioning complete – companyId={} branchId={} mappingsCreated={}",
                companyId, branchId, mappingsCreated);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Account seeding
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Inserts all default accounts for the company.
     * Header accounts are inserted before their children so parent UUIDs
     * can be resolved when creating child accounts.
     */
    private void seedDefaultAccounts(int companyId) {
        List<AccountSeed> seeds = buildDefaultAccountSeeds();
        // Parent-first order is guaranteed by the list ordering in buildDefaultAccountSeeds().
        // We maintain a code→UUID map so children can reference their parent.
        Map<String, UUID> insertedIds = new HashMap<>();

        for (AccountSeed seed : seeds) {
            if (dbFinanceSetup.accountCodeExists(companyId, seed.code, null)) {
                // Resolve existing parent UUIDs even for skipped accounts
                FinanceAccountItem existing = findAccountByCode(companyId, seed.code);
                if (existing != null) {
                    insertedIds.put(seed.code, existing.getAccountId());
                }
                log.debug("Finance account {} already exists for company {} – skipping", seed.code, companyId);
                continue;
            }

            UUID parentId = seed.parentCode != null ? insertedIds.get(seed.parentCode) : null;

            FinanceAccountCreateRequest request = buildAccountRequest(companyId, seed, parentId);

            // Resolve account path + level (mirrors FinanceSetupService.resolveAccountHierarchy)
            String accountPath;
            int accountLevel;
            if (parentId == null) {
                accountPath = seed.code;
                accountLevel = 0;
            } else {
                FinanceAccountItem parent = dbFinanceSetup.getAccountById(companyId, parentId);
                accountPath = parent.getAccountPath() + "." + seed.code;
                accountLevel = parent.getAccountLevel() + 1;
            }

            FinanceAccountItem created = dbFinanceSetup.createAccount(request, accountPath, accountLevel);
            insertedIds.put(seed.code, created.getAccountId());
            log.debug("Finance account {} created for company {} – id={}", seed.code, companyId, created.getAccountId());
        }
    }

    /**
     * Reads back the current list of accounts for the company and returns a
     * map from account_code → account_id.
     */
    private Map<String, UUID> buildAccountCodeMap(int companyId) {
        Map<String, UUID> map = new HashMap<>();
        for (FinanceAccountItem account : dbFinanceSetup.getAccounts(companyId)) {
            map.put(account.getAccountCode(), account.getAccountId());
        }
        return map;
    }

    /**
     * Finds an existing account by code without throwing if the account is
     * missing (returns null instead).
     */
    private FinanceAccountItem findAccountByCode(int companyId, String code) {
        try {
            return dbFinanceSetup.getAccounts(companyId).stream()
                    .filter(a -> code.equals(a.getAccountCode()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mapping seeding
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Inserts branch-scoped account mappings for the given branch.
     *
     * @return the number of mappings actually inserted (skipped ones not counted)
     */
    private int seedBranchMappings(int companyId, int branchId, Map<String, UUID> accountsByCode) {
        List<MappingSeed> seeds = buildDefaultMappingSeeds();
        int created = 0;

        for (MappingSeed seed : seeds) {
            UUID accountId = accountsByCode.get(seed.accountCode);
            if (accountId == null) {
                log.warn("Finance mapping seed skipped – account code {} not found in company {}",
                        seed.accountCode, companyId);
                continue;
            }

            // Skip if an active mapping already exists for this key + branch
            boolean conflict = dbFinanceSetup.accountMappingHasActiveConflict(
                    companyId,
                    branchId,
                    null,
                    seed.mappingKey,
                    MAPPING_EFFECTIVE_FROM,
                    null,
                    null);

            if (conflict) {
                log.debug("Finance mapping {} already active for branch {} – skipping", seed.mappingKey, branchId);
                continue;
            }

            FinanceAccountMappingCreateRequest request = new FinanceAccountMappingCreateRequest();
            request.setCompanyId(companyId);
            request.setBranchId(branchId);
            request.setMappingKey(seed.mappingKey);
            request.setAccountId(accountId);
            request.setPriority(100);
            request.setEffectiveFrom(MAPPING_EFFECTIVE_FROM);
            request.setEffectiveTo(null);
            request.setStatus("active");

            dbFinanceSetup.createAccountMapping(request);
            created++;
            log.debug("Finance mapping {} → {} created for branch {}", seed.mappingKey, seed.accountCode, branchId);
        }

        return created;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Default data definitions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns the ordered list of default accounts.
     * <strong>Order matters:</strong> parents must appear before their children.
     */
    private List<AccountSeed> buildDefaultAccountSeeds() {
        List<AccountSeed> s = new ArrayList<>();

        // ── Assets ─────────────────────────────────────────────────────────
        s.add(header("1000", "Assets",            "asset",     "debit",  null));
        s.add(header("1010", "Cash and Bank",      "asset",     "debit",  "1000"));
        s.add(detail("1011", "Cash Drawer - Main Branch",    "asset", "debit", "1010",
                true,  false, false, false, false));
        s.add(detail("1012", "Cash Safe - Main Branch",      "asset", "debit", "1010",
                true,  false, false, false, false));
        s.add(header("1020", "Bank Accounts",      "asset",     "debit",  "1000"));
        s.add(detail("1021", "Main Bank Account",  "asset",     "debit",  "1020",
                true,  false, false, false, false));
        s.add(header("1030", "Payment Clearing",   "asset",     "debit",  "1000"));
        s.add(detail("1031", "Card Clearing - Paymob",    "asset", "debit", "1030",
                true,  false, false, false, false));
        s.add(detail("1032", "Wallet Clearing - Instapay","asset", "debit", "1030",
                true,  false, false, false, false));
        s.add(detail("1100", "Accounts Receivable","asset",     "debit",  "1000",
                true,  true,  false, false, false));
        s.add(detail("1200", "Inventory Asset",    "asset",     "debit",  "1000",
                true,  false, false, false, false));
        s.add(detail("1300", "Input VAT Receivable","asset",    "debit",  "1000",
                true,  false, false, false, false));

        // ── Liabilities ────────────────────────────────────────────────────
        s.add(header("2000", "Liabilities",        "liability", "credit", null));
        s.add(detail("2100", "Accounts Payable",   "liability", "credit", "2000",
                true,  false, true,  false, false));
        s.add(detail("2110", "Client Trade-In Payables", "liability", "credit", "2000",
                true,  true,  false, false, false));
        s.add(detail("2200", "Output VAT Payable", "liability", "credit", "2000",
                true,  false, false, false, false));
        s.add(detail("2300", "Customer Deposits",  "liability", "credit", "2000",
                true,  true,  false, false, false));
        s.add(detail("2400", "GRNI",               "liability", "credit", "2000",
                true,  false, true,  false, false));
        s.add(detail("2500", "Salaries Payable",   "liability", "credit", "2000",
                true,  false, false, false, false));

        // ── Equity ─────────────────────────────────────────────────────────
        s.add(header("3000", "Equity",             "equity",    "credit", null));
        s.add(detail("3100", "Owner Capital",      "equity",    "credit", "3000",
                false, false, false, false, false));
        s.add(detail("3200", "Retained Earnings",  "equity",    "credit", "3000",
                false, false, false, false, false));
        s.add(detail("3300", "Owner Drawings",     "equity",    "debit",  "3000",
                false, false, false, false, false));

        // ── Revenue ────────────────────────────────────────────────────────
        s.add(header("4000", "Revenue",            "revenue",   "credit", null));
        s.add(detail("4100", "Product Sales Revenue","revenue", "credit", "4000",
                true,  false, false, false, false));
        s.add(detail("4200", "Service Revenue",    "revenue",   "credit", "4000",
                true,  false, false, false, false));
        s.add(detail("4300", "Sales Returns",      "revenue",   "debit",  "4000",
                true,  false, false, false, false));
        s.add(detail("4400", "Sales Discounts",    "revenue",   "debit",  "4000",
                true,  false, false, false, false));

        // ── COGS ───────────────────────────────────────────────────────────
        s.add(detail("5000", "Cost of Goods Sold", "expense",   "debit",  null,
                true,  false, false, false, false));

        // ── Operating Expenses ─────────────────────────────────────────────
        s.add(header("6000", "Operating Expenses", "expense",   "debit",  null));
        s.add(detail("6100", "Rent Expense",        "expense",  "debit",  "6000",
                true,  false, false, false, false));
        s.add(detail("6200", "Salary Expense",      "expense",  "debit",  "6000",
                true,  false, false, false, false));
        s.add(detail("6300", "Utilities Expense",   "expense",  "debit",  "6000",
                true,  false, false, false, false));
        s.add(detail("6400", "Payment Provider Fees","expense", "debit",  "6000",
                true,  false, false, false, false));
        s.add(detail("6600", "Billing Credits Expense","expense", "debit", "6000",
                true,  false, false, false, false));
        s.add(detail("6500", "Inventory Damage Expense",    "expense", "debit", "6000",
                true,  false, false, false, false));
        s.add(detail("6510", "Inventory Write-Off Expense", "expense", "debit", "6000",
                true,  false, false, false, false));

        return s;
    }

    /**
     * Returns the ordered list of branch-scoped mapping seeds.
     * Each entry maps a system mapping key to a default account code.
     */
    private List<MappingSeed> buildDefaultMappingSeeds() {
        List<MappingSeed> s = new ArrayList<>();

        // POS
        s.add(new MappingSeed("pos.sales",         "4100"));
        s.add(new MappingSeed("pos.cash",          "1011"));
        s.add(new MappingSeed("pos.card",          "1031"));
        s.add(new MappingSeed("pos.wallet",        "1032"));
        s.add(new MappingSeed("pos.receivable",    "1100"));
        s.add(new MappingSeed("pos.discount",      "4400"));
        s.add(new MappingSeed("pos.output_vat",    "2200"));
        s.add(new MappingSeed("pos.cogs",          "5000"));
        s.add(new MappingSeed("pos.inventory",     "1200"));
        s.add(new MappingSeed("pos.sales_returns", "4300"));

        // Purchase
        s.add(new MappingSeed("purchase.inventory",  "1200"));
        s.add(new MappingSeed("purchase.input_vat",  "1300"));
        s.add(new MappingSeed("purchase.payable",    "2100"));
        s.add(new MappingSeed("purchase.client_payable", "2110"));
        s.add(new MappingSeed("purchase.grni",       "2400"));
        s.add(new MappingSeed("purchase.cash",       "1011"));
        s.add(new MappingSeed("purchase.bank",       "1021"));
        s.add(new MappingSeed("purchase.card",       "1031"));
        s.add(new MappingSeed("purchase.wallet",     "1032"));

        // Inventory
        s.add(new MappingSeed("inventory.asset",           "1200"));
        s.add(new MappingSeed("inventory.damage_expense",  "6500"));
        s.add(new MappingSeed("inventory.writeoff_expense","6510"));

        // Payment
        s.add(new MappingSeed("payment.cash",          "1011"));
        s.add(new MappingSeed("payment.card",          "1031"));
        s.add(new MappingSeed("payment.wallet",        "1032"));
        s.add(new MappingSeed("payment.bank",          "1021"));
        s.add(new MappingSeed("payment.cash_drawer",   "1011"));
        s.add(new MappingSeed("payment.cash_safe",     "1012"));
        s.add(new MappingSeed("payment.card_clearing", "1031"));
        s.add(new MappingSeed("payment.customer_deposits", "2300"));
        s.add(new MappingSeed("payment.billing_credit_expense", "6600"));
        s.add(new MappingSeed("payment.fee_expense",   "6400"));

        return s;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Builder helpers
    // ════════════════════════════════════════════════════════════════════════

    private FinanceAccountCreateRequest buildAccountRequest(int companyId,
                                                            AccountSeed seed,
                                                            UUID parentId) {
        FinanceAccountCreateRequest r = new FinanceAccountCreateRequest();
        r.setCompanyId(companyId);
        r.setAccountCode(seed.code);
        r.setAccountName(seed.name);
        r.setAccountType(seed.type);
        r.setNormalBalance(seed.normalBalance);
        r.setParentAccountId(parentId);
        r.setPostable(seed.postable);
        r.setSystem(false);
        r.setStatus("active");
        r.setCurrencyCode("EGP");
        r.setRequiresBranch(seed.requiresBranch);
        r.setRequiresCustomer(seed.requiresCustomer);
        r.setRequiresSupplier(seed.requiresSupplier);
        r.setRequiresProduct(seed.requiresProduct);
        r.setRequiresCostCenter(seed.requiresCostCenter);
        return r;
    }

    // ── Header account factory (non-postable, no dimensions) ────────────────

    private AccountSeed header(String code, String name, String type, String normalBalance, String parentCode) {
        return new AccountSeed(code, name, type, normalBalance, parentCode,
                false, false, false, false, false, false);
    }

    // ── Detail account factory (postable, caller specifies dimensions) ───────

    private AccountSeed detail(String code, String name, String type, String normalBalance, String parentCode,
                               boolean requiresBranch, boolean requiresCustomer,
                               boolean requiresSupplier, boolean requiresProduct, boolean requiresCostCenter) {
        return new AccountSeed(code, name, type, normalBalance, parentCode,
                true, requiresBranch, requiresCustomer, requiresSupplier, requiresProduct, requiresCostCenter);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Internal data holders
    // ════════════════════════════════════════════════════════════════════════

    private record AccountSeed(
            String code,
            String name,
            String type,
            String normalBalance,
            String parentCode,
            boolean postable,
            boolean requiresBranch,
            boolean requiresCustomer,
            boolean requiresSupplier,
            boolean requiresProduct,
            boolean requiresCostCenter
    ) {}

    private record MappingSeed(String mappingKey, String accountCode) {}
}
