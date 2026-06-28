package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.transport.InMemoryTaskTransport;

/**
 * End-to-end integration test of {@link TaskTelemetry} with the real
 * {@link InMemoryTaskTransport}: starting executions, runtime-side filtering and
 * unsubscription must work together with no mocks. The heartbeat is disabled to
 * keep the assertions deterministic.
 */
class TaskTelemetryIT {

    private final List<TaskEvent> received = new ArrayList<>();
    private final TaskTelemetry telemetry = TaskTelemetry.builder()
            .transport(new InMemoryTaskTransport())
            .clock(Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC))
            .heartbeatInterval(null)
            .executionIdGenerator(sequentialExecutionIds())
            .build();

    @AfterEach
    void closeTelemetry() {
        telemetry.close();
    }

    @Test
    void taskNameFilterDeliversOnlyMatchingExecution() {
        telemetry.listen().taskName("IMPORT_A").onEvent(received::add).start();

        try (TaskReporter reporter = telemetry.start("IMPORT_A")) {
            reporter.progress(50, "working");
            reporter.completed("done");
        }
        try (TaskReporter reporter = telemetry.start("IMPORT_B")) {
            reporter.completed("done");
        }

        assertThat(received).extracting(TaskEvent::taskName).containsOnly("IMPORT_A");
        assertThat(received).extracting(TaskEvent::type).containsExactly(
                TaskEventType.STARTED, TaskEventType.PROGRESS, TaskEventType.COMPLETED);
    }

    @Test
    void eventTypeFilterDeliversOnlyTerminalSuccess() {
        telemetry.listen().eventType(TaskEventType.COMPLETED).onEvent(received::add).start();

        try (TaskReporter reporter = telemetry.start("IMPORT_A")) {
            reporter.progress(10, "working");
            reporter.completed("done");
        }

        assertThat(received).extracting(TaskEvent::type).containsExactly(TaskEventType.COMPLETED);
    }

    @Test
    void stoppedListenerReceivesNoFurtherEvents() {
        ListenerHandle handle = telemetry.listen().onEvent(received::add).start();

        try (TaskReporter reporter = telemetry.start("IMPORT_A")) {
            reporter.completed("done");
        }
        handle.stop();
        try (TaskReporter reporter = telemetry.start("IMPORT_A")) {
            reporter.completed("done");
        }

        assertThat(received).extracting(TaskEvent::type).containsExactly(
                TaskEventType.STARTED, TaskEventType.COMPLETED);
    }

    private static Supplier<String> sequentialExecutionIds() {
        AtomicInteger counter = new AtomicInteger();
        return () -> "exec-" + counter.incrementAndGet();
    }
}
