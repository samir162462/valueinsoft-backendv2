package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTaskSchedulerDisabledTest {

    @Test
    void dequeuedCycleRechecksRuntimeGateBeforeEnteringWorker() {
        NotificationControlGate gate = mock(NotificationControlGate.class);
        NotificationWorkerTask task = mock(NotificationWorkerTask.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationWorkerTask> tasks = mock(ObjectProvider.class);
        when(tasks.orderedStream()).thenReturn(Stream.of(task));
        when(task.component()).thenReturn(NotificationComponent.FANOUT);
        when(task.delay()).thenReturn(Duration.ofSeconds(30));
        when(gate.isEnabled(NotificationComponent.FANOUT)).thenReturn(false);

        NotificationTaskScheduler scheduler = new NotificationTaskScheduler(
                new NotificationProperties(),
                gate,
                new SimpleMeterRegistry(),
                tasks);

        scheduler.runGuarded(task);

        verify(task, never()).runCycle();
    }
}
