package com.example.valueinsoftbackend.Service.billing;

import com.example.valueinsoftbackend.DatabaseRequests.DbBillingWriteModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAuditWriter;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingOperationResponse;
import com.example.valueinsoftbackend.ai.audit.AiUsageLogRepository;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Market-practice metered billing for AI usage: once a month, the client-billable
 * cost accumulated in ai_usage_log is turned into one "ai_usage" invoice per
 * company (one line per model). The invoice is payable through every existing
 * channel — company balance sweep, Paymob checkout, or manual InstaPay/cash.
 */
@Service
@Slf4j
public class AiUsageBillingService {

    public static final String AI_USAGE_SOURCE_TYPE = "ai_usage";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final AiUsageLogRepository aiUsageLogRepository;
    private final DbBillingWriteModels dbBillingWriteModels;
    private final DbCompany dbCompany;
    private final BillingAccountService billingAccountService;
    private final AiProperties aiProperties;
    private final DbPlatformAdminAuditWriter dbPlatformAdminAuditWriter;

    public AiUsageBillingService(AiUsageLogRepository aiUsageLogRepository,
                                 DbBillingWriteModels dbBillingWriteModels,
                                 DbCompany dbCompany,
                                 BillingAccountService billingAccountService,
                                 AiProperties aiProperties,
                                 DbPlatformAdminAuditWriter dbPlatformAdminAuditWriter) {
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.dbBillingWriteModels = dbBillingWriteModels;
        this.dbCompany = dbCompany;
        this.billingAccountService = billingAccountService;
        this.aiProperties = aiProperties;
        this.dbPlatformAdminAuditWriter = dbPlatformAdminAuditWriter;
    }

    @Transactional
    public PlatformBillingOperationResponse runMonthlyAiUsageBilling(YearMonth billingMonth, String actorUserName) {
        LocalDateTime from = billingMonth.atDay(1).atStartOfDay();
        LocalDateTime to = billingMonth.plusMonths(1).atDay(1).atStartOfDay();
        String monthKey = billingMonth.format(MONTH_FORMAT);

        try {
            List<AiUsageLogRepository.AiUsageBillingAggregate> aggregates =
                    aiUsageLogRepository.aggregateCompanyUsage(from, to);

            Map<Long, List<AiUsageLogRepository.AiUsageBillingAggregate>> byCompany = new LinkedHashMap<>();
            for (AiUsageLogRepository.AiUsageBillingAggregate aggregate : aggregates) {
                byCompany.computeIfAbsent(aggregate.companyId(), key -> new java.util.ArrayList<>()).add(aggregate);
            }

            int generated = 0;
            int skipped = 0;
            for (Map.Entry<Long, List<AiUsageLogRepository.AiUsageBillingAggregate>> entry : byCompany.entrySet()) {
                if (createInvoiceForCompany(entry.getKey(), entry.getValue(), monthKey, billingMonth)) {
                    generated++;
                } else {
                    skipped++;
                }
            }

            PlatformBillingOperationResponse response = new PlatformBillingOperationResponse(
                    "ai_usage_billing_cycle",
                    byCompany.size(),
                    generated,
                    skipped,
                    new Timestamp(System.currentTimeMillis())
            );
            audit(actorUserName, monthKey,
                    "{\"processedItems\":" + response.getProcessedItems()
                            + ",\"generatedItems\":" + response.getGeneratedItems()
                            + ",\"skippedItems\":" + response.getSkippedItems() + "}",
                    "success");
            return response;
        } catch (RuntimeException ex) {
            audit(actorUserName, monthKey, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}", "failed");
            throw ex;
        }
    }

    private boolean createInvoiceForCompany(long companyId,
                                            List<AiUsageLogRepository.AiUsageBillingAggregate> lines,
                                            String monthKey,
                                            YearMonth billingMonth) {
        String sourceId = "ai-usage-" + companyId + "-" + monthKey;
        if (dbBillingWriteModels.findInvoiceIdBySource(AI_USAGE_SOURCE_TYPE, sourceId) != null) {
            return false;
        }

        BigDecimal total = lines.stream()
                .map(AiUsageLogRepository.AiUsageBillingAggregate::billableCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        AiProperties.UsageBillingProperties billing = aiProperties.getUsageBilling();
        BigDecimal minimum = billing == null || billing.getMinimumMonthlyChargeEgp() == null
                ? BigDecimal.ZERO
                : billing.getMinimumMonthlyChargeEgp();
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (total.compareTo(minimum) < 0) {
            total = minimum.setScale(2, RoundingMode.HALF_UP);
        }

        Company company = dbCompany.getCompanyById(Math.toIntExact(companyId));
        if (company == null) {
            log.warn("Skipping AI usage invoice for unknown company {}", companyId);
            return false;
        }
        long billingAccountId = billingAccountService.ensureBillingAccount(company);

        long invoiceId = dbBillingWriteModels.createInvoice(
                billingAccountId,
                "AIU-" + companyId + "-" + monthKey,
                "open",
                "EGP",
                total,
                total,
                total,
                Timestamp.valueOf(LocalDateTime.now().plusDays(14)),
                AI_USAGE_SOURCE_TYPE,
                sourceId,
                "{\"source\":\"ai_usage_billing\",\"billingMonth\":\"" + billingMonth + "\"}"
        );

        for (AiUsageLogRepository.AiUsageBillingAggregate line : lines) {
            dbBillingWriteModels.createInvoiceLine(
                    invoiceId,
                    null,
                    "AI usage " + billingMonth + " — " + line.modelName()
                            + " (" + line.totalTokens() + " tokens, " + line.requestCount() + " requests)",
                    1,
                    line.billableCost().setScale(2, RoundingMode.HALF_UP),
                    line.billableCost().setScale(2, RoundingMode.HALF_UP),
                    "{\"source\":\"ai_usage_billing\",\"modelName\":\"" + escapeJson(line.modelName())
                            + "\",\"totalTokens\":" + line.totalTokens()
                            + ",\"requestCount\":" + line.requestCount() + "}"
            );
        }
        log.info("AI usage invoice {} created for company {} month {} total {}", invoiceId, companyId, monthKey, total);
        return true;
    }

    private void audit(String actorUserName, String monthKey, String contextJson, String resultStatus) {
        if (actorUserName == null || actorUserName.trim().isEmpty()) {
            return;
        }
        dbPlatformAdminAuditWriter.createAuditEvent(
                actorUserName,
                "platform.admin.write",
                "platform.billing.ai_usage.run",
                null,
                null,
                "{\"billingMonth\":\"" + monthKey + "\"}",
                contextJson,
                resultStatus
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
