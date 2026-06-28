package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.heartbeat.HeartbeatHandle;
import org.tasktelemetry.heartbeat.HeartbeatScheduler;
import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.listener.TaskListener;
import org.tasktelemetry.transport.TaskTransport;

@ExtendWith(MockitoExtension.class)
class TaskTelemetryTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-28T10:00:00Z");

    @Mock
    private TaskTransport transport;

    @Mock
    private TaskListener listener;

    @Test
    void startEmitsStartedWithGeneratedExecutionId() {
        try (TaskTelemetry telemetry = telemetry()) {
            telemetry.start("IMPORT_CLIENTI");

            TaskEvent started = publishedEvents().get(0);
            assertThat(started.type()).isEqualTo(TaskEventType.STARTED);
            assertThat(started.taskName()).isEqualTo("IMPORT_CLIENTI");
            assertThat(started.executionId()).isEqualTo("exec-fixed");
            assertThat(started.timestamp()).isEqualTo(FIXED_INSTANT);
        }
    }

    @Test
    void startWithCorrelationKeyPropagatesIt() {
        try (TaskTelemetry telemetry = telemetry()) {
            telemetry.start("IMPORT_CLIENTI", "pratica-556101");

            assertThat(publishedEvents().get(0).correlationKey()).isEqualTo("pratica-556101");
        }
    }

    @Test
    void listenSubscribesListenerAndHandleUnsubscribes() {
        try (TaskTelemetry telemetry = telemetry()) {
            ListenerHandle handle =
                    telemetry.listen().taskName("IMPORT_CLIENTI").onEvent(listener).start();
            handle.stop();

            verify(transport, times(1)).subscribe(any());
            verify(transport, times(1)).unsubscribe(any());
        }
    }

    @Test
    void startWithoutListenerThrows() {
        try (TaskTelemetry telemetry = telemetry()) {
            assertThatNullPointerException()
                    .isThrownBy(() -> telemetry.listen().taskName("IMPORT_CLIENTI").start());
        }
    }

    @Test
    void builderCloseBehaviorIsAppliedToReporters() {
        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .transport(transport)
                .clock(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC))
                .heartbeatInterval(null)
                .closeBehavior(TaskReporter.CloseBehavior.FAILED)
                .executionIdGenerator(() -> "exec-fixed")
                .build()) {

            telemetry.start("IMPORT_CLIENTI").close();

            assertThat(publishedEvents())
                    .extracting(TaskEvent::type)
                    .containsExactly(TaskEventType.STARTED, TaskEventType.FAILED);
        }
    }

    @Test
    void builderHeartbeatSchedulerIsUsedByReporters() {
        ManualHeartbeatScheduler heartbeatScheduler = new ManualHeartbeatScheduler();
        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .transport(transport)
                .clock(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC))
                .heartbeatScheduler(heartbeatScheduler)
                .heartbeatInterval(Duration.ofSeconds(5))
                .executionIdGenerator(() -> "exec-fixed")
                .build()) {

            telemetry.start("IMPORT_CLIENTI");
            heartbeatScheduler.fireTick();
            heartbeatScheduler.fireTick();

            assertThat(publishedEvents())
                    .extracting(TaskEvent::type)
                    .contains(TaskEventType.HEARTBEAT);
        }
    }

    private TaskTelemetry telemetry() {
        return TaskTelemetry.builder()
                .transport(transport)
                .clock(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC))
                .heartbeatInterval(null)
                .executionIdGenerator(() -> "exec-fixed")
                .build();
    }

    private List<TaskEvent> publishedEvents() {
        ArgumentCaptor<TaskEvent> captor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(transport, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Heartbeat scheduler driven by the test: it captures the scheduled task and
     * runs it only when {@link #fireTick()} is called.
     */
    private static final class ManualHeartbeatScheduler implements HeartbeatScheduler {

        private Runnable tick;

        @Override
        public HeartbeatHandle schedule(Runnable heartbeatTask, Duration interval) {
            this.tick = heartbeatTask;
            return () -> {
            };
        }

        private void fireTick() {
            tick.run();
        }
    }
}
