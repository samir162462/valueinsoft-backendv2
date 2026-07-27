package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFanOutJob;
import com.example.valueinsoftbackend.notification.scheduler.NotificationWorkSignal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import java.util.Optional;

class NotificationPublisherDisabledTest {
    @Test
    void disabledModuleIssuesZeroQueries() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> gates = mock(ObjectProvider.class);
        NotificationIdempotencyService idempotency = mock(NotificationIdempotencyService.class);
        DbNotificationEvent events = mock(DbNotificationEvent.class);
        DbNotificationFanOutJob jobs = mock(DbNotificationFanOutJob.class);
        NotificationPublisher publisher =
                new NotificationPublisher(properties, gates, idempotency, events, jobs);

        var result = publisher.publish(NotificationRequest.builder(
                1, "pos.order.voided", "order:1").build());

        assertThat(result.suppressed()).isTrue();
        verifyNoInteractions(gates, idempotency, events, jobs);
    }

    @Test
    void runtimeMasterSwitchIsCheckedBeforeOpeningTransaction() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> gates = mock(ObjectProvider.class);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gates.getIfAvailable()).thenReturn(gate);
        when(gate.isEnabled(
                com.example.valueinsoftbackend.notification.control.NotificationComponent.PUBLISH))
                .thenReturn(false);

        NotificationIdempotencyService idempotency = mock(NotificationIdempotencyService.class);
        DbNotificationEvent events = mock(DbNotificationEvent.class);
        DbNotificationFanOutJob jobs = mock(DbNotificationFanOutJob.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        NotificationPublisher publisher =
                new NotificationPublisher(properties, gates, idempotency, events, jobs);
        publisher.configureTransactionManager(transactionManager);

        var result = publisher.publish(NotificationRequest.builder(
                1, "pos.order.voided", "order:1").build());

        assertThat(result.suppressed()).isTrue();
        verifyNoInteractions(transactionManager, idempotency, events, jobs);
    }

    @Test
    void newlyCommittedEventSignalsParkedFanOutWorker() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> gates = mock(ObjectProvider.class);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gates.getIfAvailable()).thenReturn(gate);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        NotificationIdempotencyService idempotency = mock(NotificationIdempotencyService.class);
        DbNotificationEvent events = mock(DbNotificationEvent.class);
        DbNotificationFanOutJob jobs = mock(DbNotificationFanOutJob.class);
        NotificationWorkSignal workSignal = mock(NotificationWorkSignal.class);
        NotificationRequest request = mock(NotificationRequest.class);
        byte[] fingerprint = new byte[] {1, 2, 3};
        when(idempotency.fingerprint(request)).thenReturn(fingerprint);
        when(events.insert(request, fingerprint)).thenReturn(Optional.of(42L));
        when(request.companyId()).thenReturn(1095L);

        NotificationPublisher publisher =
                new NotificationPublisher(properties, gates, idempotency, events, jobs);
        publisher.configureWorkSignal(workSignal);

        publisher.publish(request);

        verify(workSignal).signal(NotificationComponent.FANOUT);
    }
}
