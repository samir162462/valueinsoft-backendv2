package com.example.valueinsoftbackend.notification.producer;

import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.service.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationPilotProducerTest {

    @Test
    void shiftOpenedBuildsCatalogBackedIdempotentEvent() {
        NotificationPublisher publisher = mock(NotificationPublisher.class);
        NotificationPilotProducer producer = new NotificationPilotProducer(publisher);

        producer.shiftOpened(1095, 1074, 24, 42, "24", "cashier", "Zag branch");

        ArgumentCaptor<NotificationRequest> request =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(publisher).publish(request.capture());
        NotificationRequest value = request.getValue();
        assertEquals(1095, value.companyId());
        assertEquals("pos.shift.opened", value.typeKey());
        assertEquals("pos.shift.opened:24", value.idempotencyKey());
        assertEquals(1074, value.branchId());
        assertEquals(42, value.actorUserId());
        assertEquals("shift", value.subjectType());
        assertEquals(24L, value.subjectId());
        assertEquals("Zag branch", value.params().get("branchName"));
        // The template renders from params, not from actorUserId — so the name and branch
        // have to be here or the notification cannot say who did it or where.
        assertEquals("cashier", value.params().get("userName"));
        assertEquals(1074, value.params().get("branchId"));
    }

    @Test
    void financePaymentReceivedCarriesAuditableDetailsAndDurableIdempotency() {
        NotificationPublisher publisher = mock(NotificationPublisher.class);
        NotificationPilotProducer producer = new NotificationPilotProducer(publisher);

        producer.financePaymentReceived(
                1095, 1074, 901, 42, "client_receipt",
                new BigDecimal("2688.00"), "EGP", "sam0001");

        ArgumentCaptor<NotificationRequest> request =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(publisher).publish(request.capture());
        NotificationRequest value = request.getValue();
        assertEquals("finance.payment.received", value.typeKey());
        assertEquals("finance.payment.received:client_receipt:901", value.idempotencyKey());
        assertEquals(901L, value.params().get("transactionId"));
        assertEquals(new BigDecimal("2688.00"), value.params().get("amount"));
        assertEquals("EGP", value.params().get("currencyCode"));
        assertEquals("sam0001", value.params().get("actorName"));
    }

    @Test
    void posPaymentReceivedUsesOrderIdToDeduplicateFullAndPartialCheckoutRetries() {
        NotificationPublisher publisher = mock(NotificationPublisher.class);
        NotificationPilotProducer producer = new NotificationPilotProducer(publisher);

        producer.posPaymentReceived(
                1095, 1074, 501, 42, "R-501",
                new BigDecimal("600.00"), "EGP", "sam0001", "PARTIAL");

        ArgumentCaptor<NotificationRequest> request =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(publisher).publish(request.capture());
        NotificationRequest value = request.getValue();
        assertEquals("pos.payment.received", value.typeKey());
        assertEquals("pos.payment.received:501", value.idempotencyKey());
        assertEquals("order", value.subjectType());
        assertEquals(501L, value.subjectId());
        assertEquals("R-501", value.params().get("transactionId"));
        assertEquals("PARTIAL", value.params().get("settlementStatus"));
    }
}
