package com.example.valueinsoftbackend.notification.producer;

import com.example.valueinsoftbackend.notification.model.NotificationPublishResult;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.service.NotificationPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Typed producer boundary for the first five business integrations. Callers supply their
 * durable business operation id as the idempotency suffix, never a random UUID.
 */
@Service
public class NotificationPilotProducer {
    private final NotificationPublisher publisher;

    public NotificationPilotProducer(NotificationPublisher publisher) {
        this.publisher = publisher;
    }

    public NotificationPublishResult lowStock(long companyId, int branchId, long productId,
                                              String operationId, String productName, Number qty,
                                              String branchName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "inventory.stock.low", "inventory.stock.low:" + operationId)
                .branchId(branchId).subject("product", productId)
                .params(Map.of("branchId", branchId, "productId", productId, "productName", productName,
                        "qty", qty, "branchName", branchName))
                .build());
    }

    /**
     * Shift closed.
     *
     * <p>{@code userName} and {@code branchName} are carried as <strong>params</strong>, not
     * merely as {@code actorUserId} and {@code branchId}. The actor id is stored on the event
     * for audit, but templates render from {@code params} only — so without the names here the
     * notification can say "Shift 412 was closed" and nothing more. Someone reading this on
     * their phone needs to know <em>who</em> closed it and <em>where</em>, without opening the
     * app to look it up.
     *
     * <p>The name reaches the in-app feed but never a lock screen: {@code pos.shift.*} is
     * {@code generic_only}, so the push preview stays the param-free generic string and no
     * employee name leaves the authenticated surface (§3.2).
     */
    public NotificationPublishResult shiftClosed(long companyId, int branchId, long shiftId,
                                                 int actorUserId, String operationId,
                                                 String userName, String branchName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "pos.shift.closed", "pos.shift.closed:" + operationId)
                .branchId(branchId).actorUserId(actorUserId).subject("shift", shiftId)
                .params(Map.of(
                        "shiftId", shiftId,
                        "branchId", branchId,
                        "branchName", safe(branchName),
                        "userName", safe(userName)))
                .build());
    }

    /** Shift opened. Same reasoning as {@link #shiftClosed} for the name and branch params. */
    public NotificationPublishResult shiftOpened(long companyId, int branchId, long shiftId,
                                                 int actorUserId, String operationId,
                                                 String userName, String branchName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "pos.shift.opened", "pos.shift.opened:" + operationId)
                .branchId(branchId).actorUserId(actorUserId).subject("shift", shiftId)
                .params(Map.of(
                        "shiftId", shiftId,
                        "branchId", branchId,
                        "branchName", safe(branchName),
                        "userName", safe(userName)))
                .build());
    }

    /**
     * {@code Map.of} rejects nulls, and a missing branch or user name must not stop the
     * notification. A placeholder renders as an honest gap; a thrown NPE loses the event.
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public NotificationPublishResult orderVoided(long companyId, int branchId, long orderId,
                                                 int actorUserId, String operationId,
                                                 String orderNumber) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "pos.order.voided", "pos.order.voided:" + operationId)
                .branchId(branchId).actorUserId(actorUserId).subject("order", orderId)
                .params(Map.of("orderId", orderId, "orderNumber", orderNumber))
                .build());
    }

    public NotificationPublishResult invoiceOverdue(long companyId, long invoiceId,
                                                    String operationId, String invoiceNumber,
                                                    Number amount, LocalDate dueDate) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "finance.invoice.overdue", "finance.invoice.overdue:" + operationId)
                .subject("invoice", invoiceId)
                .params(Map.of("companyId", companyId, "invoiceId", invoiceId, "invoiceNumber", invoiceNumber,
                        "amount", amount, "dueDate", dueDate.toString()))
                .build());
    }

    public NotificationPublishResult financePaymentReceived(long companyId,
                                                            int branchId,
                                                            long transactionId,
                                                            Integer actorUserId,
                                                            String sourceType,
                                                            BigDecimal amount,
                                                            String currencyCode,
                                                            String actorName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "finance.payment.received",
                        "finance.payment.received:" + sourceType + ":" + transactionId)
                .branchId(branchId).actorUserId(actorUserId)
                .subject("payment", transactionId)
                .params(Map.of(
                        "transactionId", transactionId,
                        "amount", amount,
                        "currencyCode", currencyCode,
                        "actorName", actorName,
                        "sourceType", sourceType))
                .build());
    }

    public NotificationPublishResult financePaymentSent(long companyId,
                                                        int branchId,
                                                        long transactionId,
                                                        Integer actorUserId,
                                                        String sourceType,
                                                        BigDecimal amount,
                                                        String currencyCode,
                                                        String actorName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "finance.payment.sent",
                        "finance.payment.sent:" + sourceType + ":" + transactionId)
                .branchId(branchId).actorUserId(actorUserId)
                .subject("payment", transactionId)
                .params(Map.of(
                        "transactionId", transactionId,
                        "amount", amount,
                        "currencyCode", currencyCode,
                        "actorName", actorName,
                        "sourceType", sourceType))
                .build());
    }

    public NotificationPublishResult posPaymentReceived(long companyId,
                                                        int branchId,
                                                        long orderId,
                                                        Integer actorUserId,
                                                        String transactionId,
                                                        BigDecimal amount,
                                                        String currencyCode,
                                                        String actorName,
                                                        String settlementStatus) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "pos.payment.received",
                        "pos.payment.received:" + orderId)
                .branchId(branchId).actorUserId(actorUserId)
                .subject("order", orderId)
                .params(Map.of(
                        "orderId", orderId,
                        "transactionId", transactionId,
                        "amount", amount,
                        "currencyCode", currencyCode,
                        "actorName", actorName,
                        "settlementStatus", settlementStatus))
                .build());
    }

    public NotificationPublishResult leaveRequested(long companyId, Integer branchId,
                                                    long leaveRequestId, int actorUserId,
                                                    int managerUserId, String operationId,
                                                    String employeeName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "hr.leave.requested", "hr.leave.requested:" + operationId)
                .branchId(branchId).actorUserId(actorUserId)
                .subject("leave_request", leaveRequestId)
                .params(Map.of("leaveRequestId", leaveRequestId,
                        "managerUserId", managerUserId,
                        "employeeName", employeeName))
                .build());
    }
}
