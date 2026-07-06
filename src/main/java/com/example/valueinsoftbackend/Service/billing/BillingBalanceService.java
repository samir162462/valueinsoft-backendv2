package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountBalanceResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountLedgerItem;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountLedgerPageResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceCreditRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceCreditResponse;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Set;

@Service
public class BillingBalanceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> CREDIT_FUNDING_SOURCES = Set.of(
            "BANK_TRANSFER_TOP_UP",
            "CASH_TOP_UP",
            "ONLINE_TOP_UP",
            "PROMOTIONAL_CREDIT",
            "SUBSCRIPTION_CREDIT",
            "MANUAL_CORRECTION",
            "REFUND_CREDIT",
            "REVERSAL"
    );
    private static final Set<String> CREDIT_REASONS = Set.of(
            "CUSTOMER_PREPAYMENT",
            "PROMOTIONAL_CREDIT",
            "SUBSCRIPTION_CREDIT",
            "MANUAL_CORRECTION",
            "REFUND_CREDIT",
            "REVERSAL"
    );

    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbCompany dbCompany;
    private final BillingAccountService billingAccountService;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public BillingBalanceService(DbBillingWriteModels dbBillingWriteModels,
                                 DbCompany dbCompany,
                                 BillingAccountService billingAccountService,
                                 FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbCompany = dbCompany;
        this.billingAccountService = billingAccountService;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public BillingAccountBalanceResponse getBalance(int companyId, String currencyCode) {
        String currency = normalizeEnabledCurrency(currencyCode);
        ensureBillingAccount(companyId);
        BillingAccountBalanceResponse response = dbBillingWriteModels.findBillingAccountBalance(companyId, currency);
        if (response == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_ACCOUNT_NOT_FOUND", "Billing account not found");
        }
        return response;
    }

    public BillingAccountLedgerPageResponse getLedger(int companyId,
                                                      String currencyCode,
                                                      String transactionType,
                                                      Integer page,
                                                      Integer size) {
        String currency = normalizeEnabledCurrency(currencyCode);
        int sanitizedPage = page == null || page < 1 ? 1 : page;
        int sanitizedSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        ensureBillingAccount(companyId);
        return new BillingAccountLedgerPageResponse(
                companyId,
                currency,
                sanitizedPage,
                sanitizedSize,
                dbBillingWriteModels.findBillingAccountLedger(
                        companyId,
                        currency,
                        normalizeOptional(transactionType),
                        sanitizedSize,
                        (sanitizedPage - 1) * sanitizedSize)
        );
    }

    @Transactional
    public BillingBalanceCreditResponse creditBalance(int companyId,
                                                      BillingBalanceCreditRequest request,
                                                      String actorName) {
        validateCreditRequest(request);
        String currency = normalizeEnabledCurrency(request.getCurrencyCode());
        ensureBillingAccount(companyId);

        BillingAccountLedgerItem existing = dbBillingWriteModels.findBillingAccountLedgerByIdempotencyKey(
                companyId,
                request.getIdempotencyKey().trim()
        );
        if (existing != null) {
            return existingCreditResponse(existing, request);
        }

        BillingAccountBalanceResponse account = dbBillingWriteModels.lockBillingAccountBalance(companyId, currency);
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_ACCOUNT_NOT_FOUND", "Billing account not found");
        }

        BigDecimal amount = money(request.getAmount());
        BigDecimal balanceBefore = money(account.getAvailableBalance());
        BigDecimal balanceAfter = balanceBefore.add(amount);
        String fundingSource = normalizeFundingSource(request.getFundingSource());
        String creditReason = normalizeCreditReason(request.getCreditReason());
        String approvalStatus = normalizeApprovalStatus(request.getApprovalStatus());
        if (!"APPROVED".equals(approvalStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_CREDIT_NOT_APPROVED", "Only approved credits can affect available balance");
        }

        dbBillingWriteModels.updateBillingAccountAvailableBalance(account.getBillingAccountId(), balanceAfter);
        long ledgerId = dbBillingWriteModels.createBillingAccountLedgerEntry(
                account.getBillingAccountId(),
                companyId,
                normalizeTransactionType(request.getTransactionType()),
                amount,
                currency,
                "CREDIT",
                balanceBefore,
                balanceAfter,
                "billing_balance_credit",
                request.getReference().trim(),
                request.getIdempotencyKey().trim(),
                fundingSource,
                creditReason,
                approvalStatus,
                firstNonBlank(request.getDescription(), request.getNote(), "Billing balance credit"),
                metadataJson(actorName, request.getNote())
        );
        financeOperationalPostingService.enqueueBillingBalanceCredit(
                companyId,
                account.getBillingAccountId(),
                ledgerId,
                amount,
                currency,
                fundingSource,
                creditReason,
                request.getReference().trim(),
                new Timestamp(System.currentTimeMillis()),
                actorName
        );

        return new BillingBalanceCreditResponse(
                companyId,
                account.getBillingAccountId(),
                ledgerId,
                amount,
                currency,
                balanceBefore,
                balanceAfter,
                fundingSource,
                creditReason,
                request.getReference().trim(),
                approvalStatus
        );
    }

    @Transactional
    public BillingBalanceCreditResponse reverseBalance(int companyId,
                                                       BillingBalanceCreditRequest request,
                                                       String actorName) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_REVERSAL_REQUEST_REQUIRED", "Reversal request is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_REVERSAL_AMOUNT_INVALID", "Reversal amount must be positive");
        }
        if (request.getReference() == null || request.getReference().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_REVERSAL_REFERENCE_REQUIRED", "reference is required");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
        }
        String currency = normalizeEnabledCurrency(request.getCurrencyCode());
        ensureBillingAccount(companyId);

        BillingAccountLedgerItem existing = dbBillingWriteModels.findBillingAccountLedgerByIdempotencyKey(
                companyId,
                request.getIdempotencyKey().trim()
        );
        if (existing != null) {
            return existingCreditResponse(existing, request);
        }

        BillingAccountBalanceResponse account = dbBillingWriteModels.lockBillingAccountBalance(companyId, currency);
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BILLING_ACCOUNT_NOT_FOUND", "Billing account not found");
        }

        BigDecimal amount = money(request.getAmount());
        BigDecimal balanceBefore = money(account.getAvailableBalance());
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BILLING_BALANCE_REVERSAL_EXCEEDS_BALANCE",
                    "Reversal amount exceeds the available balance (" + balanceBefore + ")"
            );
        }

        dbBillingWriteModels.updateBillingAccountAvailableBalance(account.getBillingAccountId(), balanceAfter);
        long ledgerId = dbBillingWriteModels.createBillingAccountLedgerEntry(
                account.getBillingAccountId(),
                companyId,
                "REVERSAL",
                amount,
                currency,
                "DEBIT",
                balanceBefore,
                balanceAfter,
                "billing_balance_reversal",
                request.getReference().trim(),
                request.getIdempotencyKey().trim(),
                "REVERSAL",
                "REVERSAL",
                "APPROVED",
                firstNonBlank(request.getDescription(), request.getNote(), "Billing balance reversal"),
                "{\"source\":\"billing_balance_reversal\",\"actorName\":\"" + escape(actorName) + "\",\"note\":\"" + escape(request.getNote()) + "\"}"
        );

        return new BillingBalanceCreditResponse(
                companyId,
                account.getBillingAccountId(),
                ledgerId,
                amount,
                currency,
                balanceBefore,
                balanceAfter,
                "REVERSAL",
                "REVERSAL",
                request.getReference().trim(),
                "APPROVED"
        );
    }

    private long ensureBillingAccount(int companyId) {
        Company company = dbCompany.getCompanyById(companyId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return billingAccountService.ensureBillingAccount(company);
    }

    private BillingBalanceCreditResponse existingCreditResponse(BillingAccountLedgerItem existing,
                                                               BillingBalanceCreditRequest request) {
        return new BillingBalanceCreditResponse(
                existing.getCompanyId(),
                existing.getBillingAccountId(),
                existing.getBillingAccountLedgerId(),
                money(existing.getAmount()),
                normalizeEnabledCurrency(existing.getCurrencyCode()),
                money(existing.getBalanceBefore()),
                money(existing.getBalanceAfter()),
                existing.getFundingSource(),
                existing.getCreditReason(),
                firstNonBlank(existing.getReferenceId(), request.getReference()),
                existing.getApprovalStatus()
        );
    }

    private void validateCreditRequest(BillingBalanceCreditRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_CREDIT_REQUEST_REQUIRED", "Credit request is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_CREDIT_AMOUNT_INVALID", "Credit amount must be positive");
        }
        if (request.getReference() == null || request.getReference().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_CREDIT_REFERENCE_REQUIRED", "reference is required");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
        }
        normalizeFundingSource(request.getFundingSource());
        normalizeCreditReason(request.getCreditReason());
    }

    private String normalizeEnabledCurrency(String currencyCode) {
        String currency = currencyCode == null || currencyCode.isBlank() ? "EGP" : currencyCode.trim().toUpperCase(Locale.ROOT);
        if ("LE".equals(currency)) {
            currency = "EGP";
        }
        if (!"EGP".equals(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_CURRENCY_NOT_ENABLED", "Only EGP billing balance is enabled");
        }
        return currency;
    }

    private String normalizeFundingSource(String value) {
        String normalized = requireCode(value, "fundingSource").toUpperCase(Locale.ROOT);
        if (!CREDIT_FUNDING_SOURCES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_FUNDING_SOURCE_INVALID", "Unsupported fundingSource");
        }
        return normalized;
    }

    private String normalizeCreditReason(String value) {
        String normalized = requireCode(value, "creditReason").toUpperCase(Locale.ROOT);
        if (!CREDIT_REASONS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_CREDIT_REASON_INVALID", "Unsupported creditReason");
        }
        return normalized;
    }

    private String normalizeApprovalStatus(String value) {
        return value == null || value.isBlank() ? "APPROVED" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTransactionType(String value) {
        return value == null || value.isBlank() ? "MANUAL_CREDIT" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireCode(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_BALANCE_CREDIT_FIELD_REQUIRED", fieldName + " is required");
        }
        return value.trim();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String metadataJson(String actorName, String note) {
        return "{\"source\":\"billing_balance_credit\",\"actorName\":\"" + escape(actorName) + "\",\"note\":\"" + escape(note) + "\"}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
