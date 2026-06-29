package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.heartbeat.HeartbeatHandle;
import org.tasktelemetry.heartbeat.HeartbeatScheduler;
import org.tasktelemetry.transport.TaskTransport;

@ExtendWith(MockitoExtension.class)
class TaskReporterHeartbeatTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-28T10:00:00Z");
    private static final TaskExecutionDescriptor DESCRIPTOR =
            new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");
    private static final Duration INTERVAL = Duration.ofSeconds(5);

    @Mock
    private TaskTransport transport;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private final ManualHeartbeatScheduler scheduler = new ManualHeartbeatScheduler();

    private TaskReporter reporter;

    @AfterEach
    void closeReporter() {
        if (reporter != null) {
            reporter.close();
        }
    }

    @Test
    void schedulesHeartbeatWithConfiguredInterval() {
        reporter = newReporter();

        assertThat(scheduler.interval).isEqualTo(INTERVAL);
        assertThat(scheduler.tick).isNotNull();
    }

    @Test
    void firstTickAfterStartedIsSuppressed() {
        reporter = newReporter();

        scheduler.fireTick();

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED);
    }

    @Test
    void heartbeatIsEmittedWhenExecutionIsSilent() {
        reporter = newReporter();

        scheduler.fireTick();
        scheduler.fireTick();

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.HEARTBEAT);
    }

    @Test
    void normalEventSuppressesNextHeartbeat() {
        reporter = newReporter();

        scheduler.fireTick();
        reporter.progress(50, "working");
        scheduler.fireTick();

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.PROGRESS);
    }

    @Test
    void closeCancelsHeartbeatAndFurtherTicksDoNothing() {
        reporter = newReporter();

        reporter.close();
        scheduler.fireTick();

        assertThat(scheduler.cancelled).isTrue();
        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.CANCELLED);
    }

    @Test
    void terminalEventCancelsHeartbeat() {
        reporter = newReporter();

        reporter.completed("done");
        scheduler.fireTick();

        assertThat(scheduler.cancelled).isTrue();
        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.COMPLETED);
    }

    @Test
    @SuppressWarnings("resource") // constructor is expected to throw, no reporter is created
    void rejectsNonPositiveInterval() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new TaskReporter(
                DESCRIPTOR, transport,
                TaskReporterSettings.defaults().withClock(clock).withHeartbeat(scheduler, Duration.ZERO)));
    }

    @Test
    @SuppressWarnings("resource") // constructor is expected to throw, no reporter is created
    void rejectsSchedulerWithoutInterval() {
        assertThatNullPointerException().isThrownBy(() -> new TaskReporter(
                DESCRIPTOR, transport,
                TaskReporterSettings.defaults().withClock(clock).withHeartbeat(scheduler, null)));
    }

    private TaskReporter newReporter() {
        return new TaskReporter(
                DESCRIPTOR, transport,
                TaskReporterSettings.defaults().withClock(clock).withHeartbeat(scheduler, INTERVAL));
    }

    private List<TaskEvent> publishedEvents() {
        ArgumentCaptor<TaskEvent> captor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(transport, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Heartbeat scheduler driven by the test: it captures the scheduled task and
     * runs it only when {@link #fireTick()} is called, so heartbeat behavior is
     * verified without any real time.
     */
    private static final class ManualHeartbeatScheduler implements HeartbeatScheduler {

        private Runnable tick;
        private Duration interval;
        private boolean cancelled;

        @Override
        public HeartbeatHandle schedule(Runnable heartbeatTask, Duration interval) {
            this.tick = heartbeatTask;
            this.interval = interval;
            return () -> cancelled = true;
        }

        private void fireTick() {
            tick.run();
        }
    }
}
