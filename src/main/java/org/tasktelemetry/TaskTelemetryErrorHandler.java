package org.tasktelemetry;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.logging.JulTaskTelemetryLogger;
import org.tasktelemetry.logging.TaskTelemetryLogger;

/**
 * Handles failures that occur while publishing a telemetry event.
 *
 * <p>By default publishing an event must not break the task that emits it (see
 * SPEC): when {@code TaskTransport.publish} throws, the runtime routes the
 * failure here instead of letting it propagate. The {@link #rethrow()} handler
 * opts back into propagation when the caller wants publication failures to be
 * fatal.
 */
@FunctionalInterface
public interface TaskTelemetryErrorHandler {

    /**
     * Handles a failed publication.
     *
     * @param event the event that could not be published
     * @param error the failure raised by the transport
     */
    void onPublishFailure(TaskEvent event, Throwable error);

    /**
     * Returns a handler that silently ignores publication failures.
     *
     * @return the no-op handler
     */
    static TaskTelemetryErrorHandler ignore() {
        return (event, error) -> {
        };
    }

    /**
     * Returns a logging handler using the
     * {@link JulTaskTelemetryLogger#DEFAULT_LOG_PREFIX default prefix}.
     *
     * @return the logging handler
     */
    static TaskTelemetryErrorHandler logging() {
        return logging(JulTaskTelemetryLogger.DEFAULT_LOG_PREFIX);
    }

    /**
     * Returns a handler that logs publication failures at warning level through
     * the default {@link TaskTelemetryLogger}, which is backed by
     * {@code java.util.logging} so the core needs no logging dependency. Every
     * message is prefixed with the given marker.
     *
     * @param logPrefix the marker prepended to every log message
     * @return the logging handler
     */
    static TaskTelemetryErrorHandler logging(String logPrefix) {
        TaskTelemetryLogger logger = new JulTaskTelemetryLogger(logPrefix);
        return (event, error) -> logger.warning(
                "Failed to publish telemetry event " + event.eventId()
                        + " for execution " + event.executionId(),
                error);
    }

    /**
     * Returns a handler that rethrows the failure, making publication failures
     * fatal for the emitting task.
     *
     * @return the rethrowing handler
     */
    static TaskTelemetryErrorHandler rethrow() {
        return (event, error) -> {
            if (error instanceof RuntimeException runtimeError) {
                throw runtimeError;
            }
            throw new IllegalStateException(
                    "Failed to publish telemetry event " + event.eventId(), error);
        };
    }
}
