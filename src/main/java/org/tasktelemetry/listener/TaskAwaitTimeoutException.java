package org.tasktelemetry.listener;

import java.io.Serial;

/**
 * Thrown by {@link ListenerRegistration#awaitStart} when no matching event is
 * received before the timeout elapses.
 *
 * <p>It is unchecked and meant to be handled at application level (for example a
 * frontend that gives up waiting for a task to start).
 */
public final class TaskAwaitTimeoutException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TaskAwaitTimeoutException(String message) {
        super(message);
    }
}
