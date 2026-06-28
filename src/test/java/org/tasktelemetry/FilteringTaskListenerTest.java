package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FilteringTaskListenerTest {

    private final List<TaskEvent> received = new ArrayList<>();

    @Test
    void withoutFiltersForwardsEveryEvent() {
        FilteringTaskListener listener = new FilteringTaskListener(null, null, null, null, received::add);

        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.STARTED));
        listener.onEvent(event("IMPORT_B", "exec-2", "key-2", TaskEventType.COMPLETED));

        assertThat(received).hasSize(2);
    }

    @Test
    void taskNameFilterForwardsOnlyMatchingTask() {
        FilteringTaskListener listener =
                new FilteringTaskListener("IMPORT_A", null, null, null, received::add);

        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.STARTED));
        listener.onEvent(event("IMPORT_B", "exec-2", "key-2", TaskEventType.STARTED));

        assertThat(received)
                .extracting(TaskEvent::taskName)
                .containsExactly("IMPORT_A");
    }

    @Test
    void executionIdFilterForwardsOnlyMatchingExecution() {
        FilteringTaskListener listener =
                new FilteringTaskListener(null, "exec-1", null, null, received::add);

        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.STARTED));
        listener.onEvent(event("IMPORT_A", "exec-2", "key-1", TaskEventType.STARTED));

        assertThat(received)
                .extracting(TaskEvent::executionId)
                .containsExactly("exec-1");
    }

    @Test
    void correlationKeyFilterDoesNotMatchEventWithoutKey() {
        FilteringTaskListener listener =
                new FilteringTaskListener(null, null, "key-1", null, received::add);

        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.STARTED));
        listener.onEvent(event("IMPORT_A", "exec-1", null, TaskEventType.STARTED));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).correlationKey()).isEqualTo("key-1");
    }

    @Test
    void eventTypeFilterForwardsOnlyMatchingType() {
        FilteringTaskListener listener =
                new FilteringTaskListener(null, null, null, TaskEventType.COMPLETED, received::add);

        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.STARTED));
        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.COMPLETED));

        assertThat(received)
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.COMPLETED);
    }

    @Test
    void combinedFiltersMustAllMatch() {
        FilteringTaskListener listener =
                new FilteringTaskListener("IMPORT_A", "exec-1", null, TaskEventType.PROGRESS, received::add);

        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.PROGRESS));
        listener.onEvent(event("IMPORT_A", "exec-2", "key-1", TaskEventType.PROGRESS));
        listener.onEvent(event("IMPORT_B", "exec-1", "key-1", TaskEventType.PROGRESS));
        listener.onEvent(event("IMPORT_A", "exec-1", "key-1", TaskEventType.STARTED));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).executionId()).isEqualTo("exec-1");
    }

    @Test
    void rejectsNullDelegate() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FilteringTaskListener(null, null, null, null, null));
    }

    private static TaskEvent event(
            String taskName, String executionId, String correlationKey, TaskEventType type) {

        return TaskEvent.builder()
                .eventId(executionId + "-0")
                .taskName(taskName)
                .executionId(executionId)
                .correlationKey(correlationKey)
                .type(type)
                .timestamp(Instant.parse("2026-06-28T10:00:00Z"))
                .sequenceNumber(0)
                .build();
    }
}
