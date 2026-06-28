package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.transport.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;

/**
 * End-to-end integration test wiring the real {@link TaskReporter},
 * {@link InMemoryTaskTransport} and a listener, with no mocks: events emitted by
 * the reporter must reach a subscribed listener live.
 */
class ReporterTransportIT {

    private static final TaskExecutionDescriptor DESCRIPTOR =
            new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC);

    private final TaskTransport transport = new InMemoryTaskTransport();
    private final List<TaskEvent> received = new ArrayList<>();

    @Test
    void successfulExecutionDeliversFullStreamToListener() {
        transport.subscribe(received::add);

        try (TaskReporter reporter = newReporter()) {
            reporter.progress(0, "Import started");
            reporter.progress(50, "Half of the file processed");
            reporter.completed("Import completed");
        }

        assertThat(received).extracting(TaskEvent::type).containsExactly(
                TaskEventType.STARTED,
                TaskEventType.PROGRESS,
                TaskEventType.PROGRESS,
                TaskEventType.COMPLETED);
    }

    @Test
    void earlyReturnWithoutTerminalDeliversCancelled() {
        transport.subscribe(received::add);

        try (TaskReporter reporter = newReporter()) {
            reporter.progress(0, "Import started");
        }

        assertThat(received).extracting(TaskEvent::type).containsExactly(
                TaskEventType.STARTED,
                TaskEventType.PROGRESS,
                TaskEventType.CANCELLED);
    }

    @Test
    void deliveredEventsKeepExecutionIdentityAndOrder() {
        transport.subscribe(received::add);

        try (TaskReporter reporter = newReporter()) {
            reporter.completed("done");
        }

        assertThat(received).allSatisfy(event -> {
            assertThat(event.taskName()).isEqualTo("IMPORT_CLIENTI");
            assertThat(event.executionId()).isEqualTo("exec-1");
            assertThat(event.correlationKey()).isEqualTo("pratica-556101");
        });
        assertThat(received)
                .extracting(TaskEvent::sequenceNumber)
                .containsExactly(0L, 1L);
    }

    private TaskReporter newReporter() {
        return new TaskReporter(DESCRIPTOR, transport, CLOCK, TaskReporter.CloseBehavior.CANCELLED);
    }
}
