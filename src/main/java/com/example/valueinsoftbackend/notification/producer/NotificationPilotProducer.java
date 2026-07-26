package com.example.valueinsoftbackend.notification.producer;

import com.example.valueinsoftbackend.notification.model.NotificationPublishResult;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.service.NotificationPublisher;
import org.springframework.stereotype.Service;

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

    public NotificationPublishResult shiftClosed(long companyId, int branchId, long shiftId,
                                                 int actorUserId, String operationId,
                                                 String branchName) {
        return publisher.publish(NotificationRequest.builder(companyId,
                        "pos.shift.closed", "pos.shift.closed:" + operationId)
                .branchId(branchId).actorUserId(actorUserId).subject("shift", shiftId)
                .params(Map.of("shiftId", shiftId, "branchName", branchName))
                .build());
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
