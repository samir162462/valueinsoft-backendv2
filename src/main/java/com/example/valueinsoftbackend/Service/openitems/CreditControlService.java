package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbArOpenItem;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Stage 5.1 (OPEN_ITEMS_IMPLEMENTATION_ROADMAP.md): credit control at the point of sale.
 *
 * <p>Exposure(client) = SUM(ar_open_item.remaining WHERE OPEN/PARTIALLY_SETTLED, company-wide)
 * − SUM(ar_credit_note.unapplied WHERE OPEN/PARTIALLY_APPLIED), in the company currency
 * (OPEN_ITEMS_REVISED_SCHEMA_PLAN.md §6).</p>
 *
 * <p>Rules:
 * <ul>
 *   <li>Branch setting {@code pos.creditControlMode} (V146): OFF → no check at all
 *       (no client lock, zero overhead — today's behavior). WARN/BLOCK → check runs.</li>
 *   <li>Client {@code credit_status} is a hard state: {@code BLOCKED} denies every credit
 *       sale, {@code HOLD} denies NEW credit (settlement stays possible elsewhere) —
 *       both regardless of WARN vs BLOCK. The mode softens only LIMIT breaches.</li>
 *   <li>Limit breach: exactly-at-limit passes; above it, BLOCK denies and WARN allows
 *       with {@code warning=true} for the POS to render.</li>
 *   <li>Walk-in credit (no client) is rejected upstream by PosSalePostingService
 *       ({@code CREDIT_SALE_CLIENT_REQUIRED}) independent of this service.</li>
 * </ul></p>
 *
 * <p>Concurrency: the check locks the tenant Client row ({@code FOR UPDATE}) inside the
 * caller's order transaction, so two simultaneous credit sales for one client serialize —
 * the second sees the first sale's open item in its exposure sum. The caller MUST be
 * transactional and MUST run the check in the same transaction as the order + open-item
 * insert (PosSalePostingService.postSale inside OrderService.createOrder is).</p>
 */
@Service
@Slf4j
public class CreditControlService {

    public static final String MODE_OFF = "OFF";
    public static final String MODE_WARN = "WARN";
    public static final String MODE_BLOCK = "BLOCK";
    private static final String SETTING_KEY = "pos.creditControlMode";

    private final DbArOpenItem repository;
    private final DbBranchSettings branchSettings;

    public CreditControlService(DbArOpenItem repository, DbBranchSettings branchSettings) {
        this.repository = repository;
        this.branchSettings = branchSettings;
    }

    public String mode(int companyId, int branchId) {
        if (branchSettings == null) {
            return MODE_OFF;
        }
        Object value = branchSettings.getEffectiveValueMap(companyId, branchId).get(SETTING_KEY);
        if (value == null) {
            return MODE_OFF;
        }
        String normalized = value.toString().trim().toUpperCase(Locale.ROOT);
        return (MODE_WARN.equals(normalized) || MODE_BLOCK.equals(normalized)) ? normalized : MODE_OFF;
    }

    /**
     * Checks a prospective credit sale. Must run inside the order transaction.
     *
     * @return the check result; {@code allowed=false} means the caller must reject the sale.
     */
    public OpenItemsWriteModels.CreditCheckResult checkCreditSale(int companyId, int branchId,
                                                                  int clientId, BigDecimal orderTotal) {
        String mode = mode(companyId, branchId);
        if (MODE_OFF.equals(mode)) {
            return new OpenItemsWriteModels.CreditCheckResult(true, false, MODE_OFF,
                    null, null, null, null, null);
        }

        OpenItemsWriteModels.ClientCreditLock client = repository.lockClientCredit(companyId, clientId);
        if (client == null) {
            return denied(mode, null, null, null, "CREDIT_CLIENT_NOT_FOUND",
                    "Client was not found for this tenant");
        }

        String status = client.creditStatus() == null ? "NORMAL"
                : client.creditStatus().trim().toUpperCase(Locale.ROOT);
        if ("BLOCKED".equals(status)) {
            return denied(mode, null, client.creditLimit(), null, "CREDIT_CLIENT_BLOCKED",
                    "Client credit status is BLOCKED; credit sales are not allowed");
        }
        if ("HOLD".equals(status)) {
            return denied(mode, null, client.creditLimit(), null, "CREDIT_CLIENT_ON_HOLD",
                    "Client credit is on hold; new credit sales are not allowed");
        }

        String currency = repository.getCompanyCurrency(companyId);
        BigDecimal exposure = repository.sumClientOpenExposure(companyId, clientId, currency)
                .subtract(repository.sumClientUnappliedCreditNotes(companyId, clientId, currency));
        BigDecimal total = orderTotal == null ? BigDecimal.ZERO : orderTotal;
        BigDecimal newExposure = exposure.add(total);
        BigDecimal limit = client.creditLimit() == null ? BigDecimal.ZERO : client.creditLimit();

        if (newExposure.compareTo(limit) <= 0) {
            return new OpenItemsWriteModels.CreditCheckResult(true, false, mode,
                    exposure, limit, newExposure, null, null);
        }

        String message = "Credit sale of " + total + " raises exposure to " + newExposure
                + " which exceeds the client limit of " + limit;
        if (MODE_BLOCK.equals(mode)) {
            return new OpenItemsWriteModels.CreditCheckResult(false, false, mode,
                    exposure, limit, newExposure, "CREDIT_LIMIT_EXCEEDED", message);
        }
        log.warn("Credit limit warning (company {}, branch {}, client {}): {}",
                companyId, branchId, clientId, message);
        return new OpenItemsWriteModels.CreditCheckResult(true, true, mode,
                exposure, limit, newExposure, "CREDIT_LIMIT_EXCEEDED", message);
    }

    private static OpenItemsWriteModels.CreditCheckResult denied(String mode, BigDecimal exposure,
                                                                 BigDecimal limit, BigDecimal newExposure,
                                                                 String code, String message) {
        return new OpenItemsWriteModels.CreditCheckResult(false, false, mode,
                exposure, limit, newExposure, code, message);
    }
}
