package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Integration-level test exercising {@link TaskEvent} and {@link TaskEventType}
 * together: it builds the full event stream of a single execution, the way a
 * future {@code TaskReporter} will, and verifies the cross-event invariants.
 */
class TaskEventLifecycleIT {

    private static final String TASK_NAME = "IMPORT_CLIENTI";
    private static final String EXECUTION_ID = "8f3b7e4d-2c28-4b93-9cc3-0e91f53d33c2";
    private static final String CORRELATION_KEY = "file-import-clienti-2026.csv";
    private static final Instant START = Instant.parse("2026-06-28T10:00:00Z");

    @Test
    void buildsConsistentExecutionStream() {
        List<TaskEvent> stream = buildImportExecutionStream();

        assertThat(stream).hasSize(4);
        assertThat(stream).allSatisfy(event -> {
            assertThat(event.taskName()).isEqualTo(TASK_NAME);
            assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
            assertThat(event.correlationKey()).isEqualTo(CORRELATION_KEY);
        });
    }

    @Test
    void sequenceNumbersAreStrictlyIncreasing() {
        List<TaskEvent> stream = buildImportExecutionStream();

        for (int index = 1; index < stream.size(); index++) {
            long previous = stream.get(index - 1).sequenceNumber();
            long current = stream.get(index).sequenceNumber();
            assertThat(current).isGreaterThan(previous);
        }
    }

    @Test
    void onlyTheLastEventIsTerminal() {
        List<TaskEvent> stream = buildImportExecutionStream();

        TaskEvent lastEvent = stream.get(stream.size() - 1);
        List<TaskEvent> nonTerminalEvents = stream.subList(0, stream.size() - 1);

        assertThat(lastEvent.type().isTerminal()).isTrue();
        assertThat(nonTerminalEvents)
                .noneMatch(event -> event.type().isTerminal());
    }

    @Test
    void startedIsFirstAndCompletedIsLast() {
        List<TaskEvent> stream = buildImportExecutionStream();

        assertThat(stream.get(0).type()).isEqualTo(TaskEventType.STARTED);
        assertThat(stream.get(stream.size() - 1).type()).isEqualTo(TaskEventType.COMPLETED);
    }

    private static List<TaskEvent> buildImportExecutionStream() {
        List<TaskEvent> stream = new ArrayList<>();
        stream.add(executionEvent(0, TaskEventType.STARTED, null, "Import started"));
        stream.add(executionEvent(1, TaskEventType.PROGRESS, 0, "File opened"));
        stream.add(executionEvent(2, TaskEventType.PROGRESS, 50, "Half of the file processed"));
        stream.add(executionEvent(3, TaskEventType.COMPLETED, 100, "Import completed"));
        return stream;
    }

    private static TaskEvent executionEvent(
            long sequenceNumber, TaskEventType type, Integer progress, String message) {

        Instant timestamp = START.plus(Duration.ofSeconds(sequenceNumber));

        return TaskEvent.builder()
                .eventId("event-" + sequenceNumber)
                .taskName(TASK_NAME)
                .executionId(EXECUTION_ID)
                .correlationKey(CORRELATION_KEY)
                .type(type)
                .timestamp(timestamp)
                .sequenceNumber(sequenceNumber)
                .progress(progress)
                .message(message)
                .build();
    }
}
