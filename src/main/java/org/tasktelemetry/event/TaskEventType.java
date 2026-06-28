package org.tasktelemetry.event;

/**
 * Type of a {@link TaskEvent} emitted during a task execution.
 *
 * <p>The terminal types ({@link #COMPLETED}, {@link #FAILED}, {@link #CANCELLED})
 * close the execution lifecycle: once one of them is emitted, no further events
 * are expected for that execution.
 */
public enum TaskEventType {

    STARTED(false),
    PROGRESS(false),
    INFO(false),
    WARNING(false),
    HEARTBEAT(false),
    COMPLETED(true),
    FAILED(true),
    CANCELLED(true),
    CUSTOM(false);

    private final boolean terminal;

    TaskEventType(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * Tells whether this type ends the execution lifecycle.
     *
     * @return {@code true} for {@link #COMPLETED}, {@link #FAILED} and
     *         {@link #CANCELLED}, {@code false} otherwise
     */
    public boolean isTerminal() {
        return terminal;
    }
}
