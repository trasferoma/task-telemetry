package org.tasktelemetry.monitor;

/**
 * Live status of an execution as derived by a {@link TaskHeartbeatMonitor} from
 * the events it receives (see SPEC §9.1).
 *
 * <p>{@link #RUNNING}, {@link #STALE} and {@link #LOST} are time-derived: they
 * depend on how long the execution has been silent. {@link #COMPLETED},
 * {@link #FAILED} and {@link #CANCELLED} are frozen once the corresponding
 * terminal event is received.
 */
public enum TaskExecutionStatus {
    RUNNING,
    STALE,
    LOST,
    COMPLETED,
    FAILED,
    CANCELLED
}
