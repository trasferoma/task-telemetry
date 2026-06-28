package org.tasktelemetry.logging;

/**
 * Logging abstraction that decouples task-telemetry from any concrete logging
 * system.
 *
 * <p>The core ships {@link JulTaskTelemetryLogger}, backed by
 * {@code java.util.logging}, so it keeps zero logging dependencies. Adapters for
 * other systems (for example Log4j or SLF4J) only need to implement this
 * interface, without touching the rest of the library.
 *
 * <p>Implementations are responsible for prepending the configured message
 * by the library shares a recognizable marker.
 */
public interface TaskTelemetryLogger {

    /**
     * Logs an informational message.
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Logs a warning message.
     *
     * @param message the message to log
     */
    void warning(String message);

    /**
     * Logs a warning message together with the throwable that caused it.
     *
     * @param message the message to log
     * @param error the related throwable
     */
    void warning(String message, Throwable error);

    /**
     * Logs an error message.
     *
     * @param message the message to log
     */
    void error(String message);

    /**
     * Logs an error message together with the throwable that caused it.
     *
     * @param message the message to log
     * @param error the related throwable
     */
    void error(String message, Throwable error);
}