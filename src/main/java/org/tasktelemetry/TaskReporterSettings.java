package org.tasktelemetry;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import org.tasktelemetry.heartbeat.HeartbeatScheduler;

/**
 * Immutable configuration shared by the reporters of a {@link TaskTelemetry}
 * runtime. It bundles the cross-cutting reporter options so {@link TaskReporter}
 * and {@link TaskTelemetry} keep small constructors.
 *
 * <p>Build it from {@link #defaults()} and the {@code with...} methods, which
 * return a new instance each time (the value object stays immutable).
 *
 * @param clock              clock used to timestamp events, required
 * @param closeBehavior      action on close without a terminal event, required
 * @param heartbeatScheduler scheduler driving the heartbeat, or {@code null} to disable it
 * @param heartbeatInterval  delay between heartbeats, or {@code null} to disable it
 * @param errorHandler       handler invoked when publishing an event fails, required
 * @param includeStackTrace  whether {@code failed} captures the throwable stack trace
 */
public record TaskReporterSettings(
        Clock clock,
        TaskReporter.CloseBehavior closeBehavior,
        HeartbeatScheduler heartbeatScheduler,
        Duration heartbeatInterval,
        TaskTelemetryErrorHandler errorHandler,
        boolean includeStackTrace) {

    public TaskReporterSettings {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(closeBehavior, "closeBehavior must not be null");
        Objects.requireNonNull(errorHandler, "errorHandler must not be null");
    }

    /**
     * Returns the default settings: system UTC clock, {@code CANCELLED} close
     * behavior, no heartbeat, logging error handler, stack trace captured.
     *
     * @return the default settings
     */
    public static TaskReporterSettings defaults() {
        return new TaskReporterSettings(
                Clock.systemUTC(),
                TaskReporter.CloseBehavior.CANCELLED,
                null,
                null,
                TaskTelemetryErrorHandler.logging(),
                true);
    }

    public TaskReporterSettings withClock(Clock clock) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler, includeStackTrace);
    }

    public TaskReporterSettings withCloseBehavior(TaskReporter.CloseBehavior closeBehavior) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler, includeStackTrace);
    }

    public TaskReporterSettings withHeartbeat(HeartbeatScheduler heartbeatScheduler, Duration heartbeatInterval) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler, includeStackTrace);
    }

    public TaskReporterSettings withErrorHandler(TaskTelemetryErrorHandler errorHandler) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler, includeStackTrace);
    }

    public TaskReporterSettings withIncludeStackTrace(boolean includeStackTrace) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler, includeStackTrace);
    }
}
