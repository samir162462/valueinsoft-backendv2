package com.example.valueinsoftbackend.notification.producer;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.Billing.BillingOverdueInvoiceCandidate;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Response.CreateOrderResult;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPilotContext;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Connects the typed pilot producer to durable business commit points. Notification
 * failures are isolated after commit and therefore can never roll back the business action.
 */
@Service
@Slf4j
public class NotificationPilotIntegrationService {
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final NotificationProperties properties;
    private final NotificationPilotProducer producer;
    private final DbNotificationPilotContext context;
    private final DbBranchSettings branchSettings;
    private NotificationAudienceResolver audience;

    public NotificationPilotIntegrationService(NotificationProperties properties,
                                               NotificationPilotProducer producer,
                                               DbNotificationPilotContext context,
                                               DbBranchSettings branchSettings) {
        this.properties = properties;
        this.producer = producer;
        this.context = context;
        this.branchSettings = branchSettings;
    }

    @Autowired(required = false)
    public void configureLeaveAudience(NotificationAudienceResolver audience) {
        this.audience = audience;
    }

    public void afterPosSale(int companyId, Order order, CreateOrderResult result) {
        if (!properties.isEnabled() || result.idempotencyHit()) {
            return;
        }
        Set<Long> productIds = new LinkedHashSet<>();
        for (OrderDetails detail : order.getOrderDetails()) {
            if (detail.getProductId() > 0) {
                productIds.add((long) detail.getProductId());
            }
        }
        if (productIds.isEmpty()) {
            return;
        }

        afterCommit("low-stock after order " + result.orderId(), () -> {
            int threshold = resolveLowStockThreshold(companyId, order.getBranchId());
            String branchName = context.branchName(companyId, order.getBranchId());
            for (DbNotificationPilotContext.LowStockProduct product :
                    context.lowStockProducts(companyId, order.getBranchId(), productIds, threshold)) {
                producer.lowStock(
                        companyId,
                        order.getBranchId(),
                        product.productId(),
                        result.orderId() + ":" + product.productId(),
                        product.productName(),
                        product.availableQuantity(),
                        branchName);
            }
        });
    }

    public void afterShiftClosed(int companyId, int branchId, long shiftId, String actorName) {
        if (!properties.isEnabled()) {
            return;
        }
        afterCommit("closed shift " + shiftId, () -> {
            Integer actorUserId = context.userId(actorName);
            if (actorUserId == null) {
                log.warn("Shift {} closed, but notification actor '{}' could not be resolved",
                        shiftId, actorName);
                return;
            }
            producer.shiftClosed(
                    companyId,
                    branchId,
                    shiftId,
                    actorUserId,
                    Long.toString(shiftId),
                    context.branchName(companyId, branchId));
        });
    }

    public void afterOrderVoided(int companyId,
                                 int branchId,
                                 DbPosOrder.OrderBounceBackContext orderContext) {
        if (!properties.isEnabled()) {
            return;
        }
        afterCommit("voided order " + orderContext.getOrderId(), () -> {
            Integer actorUserId = context.userId(orderContext.getSalesUser());
            if (actorUserId == null) {
                log.warn("Order {} was voided, but notification actor '{}' could not be resolved",
                        orderContext.getOrderId(), orderContext.getSalesUser());
                return;
            }
            producer.orderVoided(
                    companyId,
                    branchId,
                    orderContext.getOrderId(),
                    actorUserId,
                    Long.toString(orderContext.getOrderId()),
                    Long.toString(orderContext.getOrderId()));
        });
    }

    public void afterInvoiceOverdue(BillingOverdueInvoiceCandidate candidate, long dunningRunId) {
        if (!properties.isEnabled()) {
            return;
        }
        afterCommit("overdue invoice " + candidate.getBillingInvoiceId(), () -> {
            LocalDate dueDate = candidate.getDueAt().toLocalDateTime().toLocalDate();
            producer.invoiceOverdue(
                    candidate.getCompanyId(),
                    candidate.getBillingInvoiceId(),
                    Long.toString(dunningRunId),
                    Long.toString(candidate.getBillingInvoiceId()),
                    candidate.getDueAmount(),
                    dueDate);
        });
    }

    public void afterLeaveRequested(long companyId,
                                    Integer branchId,
                                    long leaveRequestId,
                                    int actorUserId,
                                    String employeeName) {
        if (!properties.isEnabled()) {
            return;
        }
        afterCommit("leave request " + leaveRequestId, () -> {
            int managerUserId = actorUserId;
            if (audience != null) {
                var managers = audience.fetchBatch(
                        companyId, branchId, "hr.leave.manage", 0, 1);
                if (!managers.isEmpty()) {
                    managerUserId = managers.get(0).userId();
                }
            }
            producer.leaveRequested(
                    companyId,
                    branchId,
                    leaveRequestId,
                    actorUserId,
                    managerUserId,
                    Long.toString(leaveRequestId),
                    employeeName);
        });
    }

    public void afterLeaveRequested(long companyId,
                                    Integer branchId,
                                    long leaveRequestId,
                                    int actorUserId,
                                    int managerUserId,
                                    String employeeName) {
        if (!properties.isEnabled()) {
            return;
        }
        afterCommit("leave request " + leaveRequestId, () -> producer.leaveRequested(
                companyId,
                branchId,
                leaveRequestId,
                actorUserId,
                managerUserId,
                Long.toString(leaveRequestId),
                employeeName));
    }

    private int resolveLowStockThreshold(int companyId, int branchId) {
        try {
            Object value = branchSettings.getEffectiveValueMap(companyId, branchId)
                    .get("inventory.lowStockThreshold");
            if (value instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            if (value instanceof String text) {
                return Math.max(0, Integer.parseInt(text.trim()));
            }
        } catch (RuntimeException exception) {
            log.warn("Could not resolve low-stock threshold for company {} branch {}; using {}: {}",
                    companyId, branchId, DEFAULT_LOW_STOCK_THRESHOLD, exception.getMessage());
        }
        return DEFAULT_LOW_STOCK_THRESHOLD;
    }

    private void afterCommit(String description, Runnable notificationWork) {
        Runnable isolated = () -> {
            try {
                notificationWork.run();
            } catch (RuntimeException exception) {
                log.warn("Business operation committed, but notification work for {} failed: {}",
                        description, exception.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            isolated.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                isolated.run();
            }
        });
    }
}
