package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.control.NotificationOperatingWindowService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTaskSchedulerDisabledTest {

    @Test
    void dequeuedCycleRechecksRuntimeGateBeforeEnteringWorker() {
        NotificationControlGate gate = mock(NotificationControlGate.class);
        NotificationOperatingWindowService operatingWindow =
                mock(NotificationOperatingWindowService.class);
        NotificationWorkSignal workSignal = mock(NotificationWorkSignal.class);
        NotificationWorkerTask task = mock(NotificationWorkerTask.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationWorkerTask> tasks = mock(ObjectProvider.class);
        when(tasks.orderedStream()).thenReturn(Stream.of(task));
        when(task.component()).thenReturn(NotificationComponent.FANOUT);
        when(task.delay()).thenReturn(Duration.ofSeconds(30));
        when(gate.isEnabled(NotificationComponent.FANOUT)).thenReturn(false);

        NotificationTaskScheduler scheduler = new NotificationTaskScheduler(
                new NotificationProperties(),
                new NotificationResourceSaverProperties(),
                gate,
                operatingWindow,
                workSignal,
                new SimpleMeterRegistry(),
                tasks);

        scheduler.runGuarded(task);

        verify(task, never()).runCycle();
    }

    @Test
    void operatingQuietWindowStopsDequeuedCycleBeforeWorkerDatabaseCode() {
        NotificationControlGate gate = mock(NotificationControlGate.class);
        NotificationOperatingWindowService operatingWindow =
                mock(NotificationOperatingWindowService.class);
        NotificationWorkSignal workSignal = mock(NotificationWorkSignal.class);
        NotificationWorkerTask task = mock(NotificationWorkerTask.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationWorkerTask> tasks = mock(ObjectProvider.class);
        when(tasks.orderedStream()).thenReturn(Stream.of(task));
        when(task.component()).thenReturn(NotificationComponent.FANOUT);
        when(gate.isEnabled(NotificationComponent.FANOUT)).thenReturn(true);
        when(operatingWindow.isQuietNow()).thenReturn(true);

        NotificationTaskScheduler scheduler = new NotificationTaskScheduler(
                new NotificationProperties(),
                new NotificationResourceSaverProperties(),
                gate,
                operatingWindow,
                workSignal,
                new SimpleMeterRegistry(),
                tasks);

        scheduler.runGuarded(task);

        verify(task, never()).runCycle();
        verify(task, never()).runEventDrivenCycle();
    }

    @Test
    void eventDrivenWorkerDrainsParksAndRearmsFromRedisSignal() throws Exception {
        NotificationControlGate gate = mock(NotificationControlGate.class);
        NotificationOperatingWindowService operatingWindow =
                mock(NotificationOperatingWindowService.class);
        NotificationWorkSignal workSignal = mock(NotificationWorkSignal.class);
        NotificationWorkerTask task = mock(NotificationWorkerTask.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationWorkerTask> tasks = mock(ObjectProvider.class);
        when(tasks.orderedStream()).thenReturn(Stream.of(task));
        when(task.component()).thenReturn(NotificationComponent.FANOUT);
        when(task.workerName()).thenReturn(NotificationComponent.FANOUT.key());
        when(task.eventDriven()).thenReturn(true);
        when(task.delay()).thenReturn(Duration.ofSeconds(30));
        when(gate.isEnabled(NotificationComponent.FANOUT)).thenReturn(true);
        when(operatingWindow.isQuietNow()).thenReturn(false);
        when(operatingWindow.nextTransitionAfter(any())).thenReturn(null);
        when(workSignal.version(NotificationComponent.FANOUT)).thenReturn(0L);

        AtomicInteger runs = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch secondRun = new CountDownLatch(1);
        when(task.runEventDrivenCycle()).thenAnswer(invocation -> {
            if (runs.incrementAndGet() == 1) {
                firstRun.countDown();
            } else {
                secondRun.countDown();
            }
            return false;
        });
        AtomicReference<BiConsumer<NotificationComponent, Long>> listener =
                new AtomicReference<>();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return null;
        }).when(workSignal).addListener(any());

        NotificationTaskScheduler scheduler = new NotificationTaskScheduler(
                new NotificationProperties(),
                new NotificationResourceSaverProperties(),
                gate,
                operatingWindow,
                workSignal,
                new SimpleMeterRegistry(),
                tasks);

        try {
            scheduler.start();
            assertThat(firstRun.await(2, TimeUnit.SECONDS)).isTrue();
            waitUntilParked(scheduler);

            when(workSignal.version(NotificationComponent.FANOUT)).thenReturn(1L);
            listener.get().accept(NotificationComponent.FANOUT, 1L);

            assertThat(secondRun.await(2, TimeUnit.SECONDS)).isTrue();
            waitUntilParked(scheduler);
            assertThat(runs).hasValue(2);
        } finally {
            scheduler.stop();
        }
    }

    private static void waitUntilParked(NotificationTaskScheduler scheduler)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!scheduler.armedWorkers().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(scheduler.armedWorkers()).isEmpty();
    }
}
