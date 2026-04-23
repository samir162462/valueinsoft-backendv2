package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Request.PayMobTransactionCallbackRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;

@Service
@Slf4j
public class PaymentProviderFinanceIntegrationService {

    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbBranch dbBranch;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public PaymentProviderFinanceIntegrationService(DbBillingWriteModels dbBillingWriteModels,
                                                    DbBranch dbBranch,
                                                    FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbBranch = dbBranch;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public void enqueuePayMobSettlement(String providerCode,
                                        String externalOrderId,
                                        String providerEventId,
                                        PayMobTransactionCallbackRequest callbackRequest) {
        if (callbackRequest == null || callbackRequest.getTransaction() == null) {
            return;
        }

        PayMobTransactionCallbackRequest.TransactionPayload transaction = callbackRequest.getTransaction();
        if (!Boolean.TRUE.equals(transaction.getSuccess())
                || Boolean.TRUE.equals(transaction.getPending())
                || Boolean.TRUE.equals(transaction.getVoided())
                || Boolean.TRUE.equals(transaction.getRefunded())) {
            return;
        }

        Integer branchId = dbBillingWriteModels.findBranchIdByExternalOrderId(providerCode, externalOrderId);
        if (branchId == null || branchId <= 0) {
            return;
        }

        Branch branch = dbBranch.getBranchById(branchId);
        String settlementMethod = normalizeSettlementMethod(transaction.getSourceData());
        String sourceType = "wallet".equals(settlementMethod) ? "wallet_settlement" : "card_settlement";
        BigDecimal grossAmount = centsToMoney(transaction.getAmountCents());
        String paymentId = providerEventId == null || providerEventId.isBlank() ? externalOrderId : providerEventId;

        LinkedHashMap<String, Object> extraPayload = new LinkedHashMap<>();
        extraPayload.put("providerCode", providerCode);
        extraPayload.put("externalOrderId", externalOrderId);
        extraPayload.put("providerEventId", providerEventId);
        extraPayload.put("callbackType", callbackRequest.getType());

        try {
            financeOperationalPostingService.enqueueImportedProviderSettlement(
                    branch.getBranchOfCompanyId(),
                    branchId,
                    sourceType,
                    settlementSourceId(providerCode, providerEventId, externalOrderId),
                    grossAmount,
                    BigDecimal.ZERO.setScale(4),
                    grossAmount,
                    settlementMethod,
                    "bank",
                    paymentId,
                    resolveSettlementTimestamp(transaction.getCreatedAt()),
                    "system",
                    extraPayload);
        } catch (RuntimeException exception) {
            log.warn(
                    "PayMob callback settlement for provider {} order {} event {} could not be enqueued to finance: {}",
                    providerCode,
                    externalOrderId,
                    providerEventId,
                    exception.getMessage()
            );
        }
    }

    private String settlementSourceId(String providerCode, String providerEventId, String externalOrderId) {
        String reference = providerEventId != null && !providerEventId.isBlank() ? providerEventId : externalOrderId;
        return "provider-callback:" + providerCode + ":" + reference;
    }

    private BigDecimal centsToMoney(Integer amountCents) {
        if (amountCents == null || amountCents <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(amountCents.longValue(), 2).setScale(4);
    }

    private Timestamp resolveSettlementTimestamp(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return new Timestamp(System.currentTimeMillis());
        }

        try {
            return Timestamp.valueOf(LocalDateTime.parse(createdAt.replace('T', ' ').replace("Z", "")));
        } catch (DateTimeParseException exception) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(createdAt.substring(0, 19)));
            } catch (RuntimeException ignored) {
                return new Timestamp(System.currentTimeMillis());
            }
        }
    }

    private String normalizeSettlementMethod(PayMobTransactionCallbackRequest.SourceDataPayload sourceData) {
        if (sourceData == null) {
            return "card";
        }

        String type = sourceData.getType() == null ? "" : sourceData.getType().trim().toLowerCase(Locale.ROOT);
        String subType = sourceData.getSubType() == null ? "" : sourceData.getSubType().trim().toLowerCase(Locale.ROOT);
        if (type.contains("wallet") || subType.contains("wallet")) {
            return "wallet";
        }
        return "card";
    }
}
