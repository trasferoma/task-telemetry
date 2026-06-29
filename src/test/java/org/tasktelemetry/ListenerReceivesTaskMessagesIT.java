package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.transport.inmemory.InMemoryTaskTransport;

/**
 * Functional end-to-end test of the typical scenario: a listener subscribes
 * through the public {@link TaskTelemetry} API and observes the full live stream
 * of messages emitted by a running task.
 */
class ListenerReceivesTaskMessagesIT {

    @Test
    void listenerObservesTheFullMessageStreamOfATask() {
        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .transport(new InMemoryTaskTransport())
                .clock(Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC))
                .heartbeatInterval(null)
                .executionIdGenerator(() -> "exec-1")
                .build()) {

            List<TaskEvent> observed = new ArrayList<>();
            telemetry.listen()
                    .taskName("IMPORT_CLIENTI")
                    .onEvent(observed::add)
                    .start();

            try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI", "file-2026.csv")) {
                reporter.progress(0, "Import started");
                reporter.info("Reading input file");
                reporter.warning("Row 42 skipped");
                reporter.progress(100, "Processing finished");
                reporter.completed("Import completed");
            }

            assertThat(observed)
                    .extracting(TaskEvent::type, TaskEvent::message, TaskEvent::progress)
                    .containsExactly(
                            tuple(TaskEventType.STARTED, null, null),
                            tuple(TaskEventType.PROGRESS, "Import started", 0),
                            tuple(TaskEventType.INFO, "Reading input file", null),
                            tuple(TaskEventType.WARNING, "Row 42 skipped", null),
                            tuple(TaskEventType.PROGRESS, "Processing finished", 100),
                            tuple(TaskEventType.COMPLETED, "Import completed", null));

            assertThat(observed).allSatisfy(event -> {
                assertThat(event.taskName()).isEqualTo("IMPORT_CLIENTI");
                assertThat(event.executionId()).isEqualTo("exec-1");
                assertThat(event.correlationKey()).isEqualTo("file-2026.csv");
            });
        }
    }
}
