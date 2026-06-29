package org.tasktelemetry.transport.crossprocess;

import java.io.Serial;

/**
 * Thrown by {@link SocketClientTaskTransport} when it cannot connect to the task
 * server: the task is not reachable (for example it has not started yet, has
 * already stopped, or the host/port is wrong).
 *
 * <p>It is unchecked and meant to be handled at application level (for example a
 * client that gives up, retries, or reports that no task is running).
 */
public final class TaskUnreachableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TaskUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
