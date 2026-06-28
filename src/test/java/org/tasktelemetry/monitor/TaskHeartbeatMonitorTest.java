package org.tasktelemetry.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;

class TaskHeartbeatMonitorTest {

    private static final Instant START = Instant.parse("2026-06-28T10:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofSeconds(15);
    private static final Duration LOST_AFTER = Duration.ofSeconds(60);
    private static final String EXECUTION_ID = "exec-1";

    private MutableClock clock;
    private TaskHeartbeatMonitor monitor;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START, ZoneOffset.UTC);
        monitor = new TaskHeartbeatMonitor(clock, STALE_AFTER, LOST_AFTER);
    }

    @Test
    void recentlyActiveExecutionIsRunning() {
        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));

        clock.advance(Duration.ofSeconds(8));

        assertThat(monitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.RUNNING);
    }

    @Test
    void silentBeyondStaleThresholdIsStale() {
        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));

        clock.advance(Duration.ofSeconds(20));

        assertThat(monitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.STALE);
    }

    @Test
    void silentBeyondLostThresholdIsLost() {
        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));

        clock.advance(Duration.ofSeconds(70));

        assertThat(monitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.LOST);
    }

    @Test
    void normalEventRefreshesLiveness() {
        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));
        clock.advance(Duration.ofSeconds(10));
        monitor.onEvent(event(EXECUTION_ID, 1, TaskEventType.PROGRESS));

        clock.advance(Duration.ofSeconds(10));

        assertThat(monitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.RUNNING);
    }

    @Test
    void completedFreezesStatusRegardlessOfTime() {
        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));
        monitor.onEvent(event(EXECUTION_ID, 1, TaskEventType.COMPLETED));

        clock.advance(Duration.ofSeconds(120));

        assertThat(monitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.COMPLETED);
    }

    @Test
    void failedAndCancelledFreezeStatus() {
        monitor.onEvent(event("exec-failed", 0, TaskEventType.FAILED));
        monitor.onEvent(event("exec-cancelled", 0, TaskEventType.CANCELLED));

        assertThat(monitor.statusOf("exec-failed")).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(monitor.statusOf("exec-cancelled")).isEqualTo(TaskExecutionStatus.CANCELLED);
    }

    @Test
    void eventsAfterTerminalDoNotChangeStatus() {
        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.COMPLETED));
        monitor.onEvent(event(EXECUTION_ID, 1, TaskEventType.PROGRESS));

        clock.advance(Duration.ofSeconds(120));

        assertThat(monitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.COMPLETED);
    }

    @Test
    void distinctExecutionsAreTrackedIndependently() {
        monitor.onEvent(event("exec-1", 0, TaskEventType.STARTED));
        clock.advance(Duration.ofSeconds(70));
        monitor.onEvent(event("exec-2", 0, TaskEventType.STARTED));

        assertThat(monitor.statusOf("exec-1")).isEqualTo(TaskExecutionStatus.LOST);
        assertThat(monitor.statusOf("exec-2")).isEqualTo(TaskExecutionStatus.RUNNING);
    }

    @Test
    void defaultThresholdsConstructorUsesFifteenAndSixtySeconds() {
        TaskHeartbeatMonitor defaultMonitor = new TaskHeartbeatMonitor(clock);
        defaultMonitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));

        clock.advance(Duration.ofSeconds(20));

        assertThat(defaultMonitor.statusOf(EXECUTION_ID)).isEqualTo(TaskExecutionStatus.STALE);
    }

    @Test
    void isTrackingReflectsObservedExecutions() {
        assertThat(monitor.isTracking(EXECUTION_ID)).isFalse();

        monitor.onEvent(event(EXECUTION_ID, 0, TaskEventType.STARTED));

        assertThat(monitor.isTracking(EXECUTION_ID)).isTrue();
    }

    @Test
    void unknownExecutionThrows() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> monitor.statusOf("nope"));
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TaskHeartbeatMonitor(clock, Duration.ZERO, LOST_AFTER));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TaskHeartbeatMonitor(clock, Duration.ofSeconds(60), Duration.ofSeconds(15)));
    }

    private static TaskEvent event(String executionId, long sequenceNumber, TaskEventType type) {
        return TaskEvent.builder()
                .eventId(executionId + "-" + sequenceNumber)
                .taskName("IMPORT_CLIENTI")
                .executionId(executionId)
                .type(type)
                .timestamp(START)
                .sequenceNumber(sequenceNumber)
                .build();
    }

    private static final class MutableClock extends Clock {

        private final ZoneId zone;
        private Instant instant;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
