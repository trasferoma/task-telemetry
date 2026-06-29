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
 */
public record TaskReporterSettings(
        Clock clock,
        TaskReporter.CloseBehavior closeBehavior,
        HeartbeatScheduler heartbeatScheduler,
        Duration heartbeatInterval,
        TaskTelemetryErrorHandler errorHandler) {

    public TaskReporterSettings {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(closeBehavior, "closeBehavior must not be null");
        Objects.requireNonNull(errorHandler, "errorHandler must not be null");
    }

    /**
     * Returns the default settings: system UTC clock, {@code CANCELLED} close
     * behavior, no heartbeat, logging error handler.
     *
     * @return the default settings
     */
    public static TaskReporterSettings defaults() {
        return new TaskReporterSettings(
                Clock.systemUTC(),
                TaskReporter.CloseBehavior.CANCELLED,
                null,
                null,
                TaskTelemetryErrorHandler.logging());
    }

    public TaskReporterSettings withClock(Clock clock) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler);
    }

    public TaskReporterSettings withCloseBehavior(TaskReporter.CloseBehavior closeBehavior) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler);
    }

    public TaskReporterSettings withHeartbeat(HeartbeatScheduler heartbeatScheduler, Duration heartbeatInterval) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler);
    }

    public TaskReporterSettings withErrorHandler(TaskTelemetryErrorHandler errorHandler) {
        return new TaskReporterSettings(
                clock, closeBehavior, heartbeatScheduler, heartbeatInterval, errorHandler);
    }
}
