package org.tasktelemetry.listener;

import java.util.Objects;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;

/**
 * Wraps a {@link TaskListener} and forwards only the events that match the
 * configured filters.
 *
 * <p>A {@code null} filter matches any value; when several filters are set, they
 * must all match (logical AND). This is the runtime-side filtering applied before
 * a listener is invoked (see SPEC).
 */
final class FilteringTaskListener implements TaskListener {

    private final String taskName;
    private final String executionId;
    private final String correlationKey;
    private final TaskEventType eventType;
    private final TaskListener delegate;

    FilteringTaskListener(
            String taskName,
            String executionId,
            String correlationKey,
            TaskEventType eventType,
            TaskListener delegate) {

        this.taskName = taskName;
        this.executionId = executionId;
        this.correlationKey = correlationKey;
        this.eventType = eventType;
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void onEvent(TaskEvent event) {
        if (matches(event)) {
            delegate.onEvent(event);
        }
    }

    private boolean matches(TaskEvent event) {
        return matchesValue(taskName, event.taskName())
                && matchesValue(executionId, event.executionId())
                && matchesValue(correlationKey, event.correlationKey())
                && matchesEventType(event.type());
    }

    private static boolean matchesValue(String filter, String value) {
        return filter == null || filter.equals(value);
    }

    private boolean matchesEventType(TaskEventType type) {
        return eventType == null || eventType == type;
    }
}
