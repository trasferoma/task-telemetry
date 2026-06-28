package org.tasktelemetry.listener;

import org.tasktelemetry.event.TaskEvent;

/**
 * Receives events emitted by task executions.
 *
 * <p>Filtering by task name, execution id, correlation key or event type is
 * applied by the runtime before the listener is invoked (see SPEC), so an
 * implementation only handles the events it is meant to receive.
 *
 * <p>This is a functional interface and can be supplied as a lambda or a method
 * reference.
 */
@FunctionalInterface
public interface TaskListener {

    /**
     * Handles a single received event.
     *
     * @param event the received event, never {@code null}
     */
    void onEvent(TaskEvent event);
}
