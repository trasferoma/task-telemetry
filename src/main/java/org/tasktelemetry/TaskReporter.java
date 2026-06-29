package org.tasktelemetry;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.heartbeat.HeartbeatHandle;
import org.tasktelemetry.heartbeat.HeartbeatScheduler;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Emits telemetry events for a single task execution.
 *
 * <p>A reporter is bound to one {@link TaskExecutionDescriptor}. It emits
 * {@link TaskEventType#STARTED} on creation and publishes every subsequent event
 * through the configured {@link TaskTransport}, generating {@code eventId},
 * {@code timestamp} (from the injected {@link Clock}) and a monotonic
 * {@code sequenceNumber}.
 *
 * <p>When a {@link HeartbeatScheduler} and an interval are supplied, the reporter
 * emits a {@link TaskEventType#HEARTBEAT} on every tick during which no other
 * event was emitted: normal events count as liveness signals and suppress the
 * next heartbeat. The heartbeat starts after {@code STARTED} and stops as soon as
 * a terminal event is emitted or the reporter is closed.
 *
 * <p>The reporter implements {@link AutoCloseable} and is meant to be used with
 * try-with-resources. If it is closed without a terminal event having been
 * emitted, it applies its {@link CloseBehavior} (by default it emits
 * {@link TaskEventType#CANCELLED}).
 *
 * <p>Once a terminal event ({@code COMPLETED}, {@code FAILED} or
 * {@code CANCELLED}) has been emitted, any further emission throws
 * {@link IllegalStateException}; {@link #close()} becomes a no-op.
 *
 * <p>Instances are safe for concurrent use: event emission is serialized so that
 * sequence numbers and lifecycle state stay consistent, including against the
 * heartbeat thread.
 */
public final class TaskReporter implements AutoCloseable {

    /**
     * Action taken when the reporter is closed without a terminal event.
     */
    public enum CloseBehavior {
        CANCELLED,
        FAILED,
        IGNORE
    }

    private static final String CLOSE_WITHOUT_TERMINAL_MESSAGE =
            "Reporter closed without a terminal event";

    private final TaskExecutionDescriptor descriptor;
    private final TaskTransport transport;
    private final Clock clock;
    private final CloseBehavior closeBehavior;
    private final TaskTelemetryErrorHandler errorHandler;

    private long nextSequenceNumber;
    private boolean terminalEmitted;
    private boolean closed;
    private boolean emittedSinceLastTick;
    private HeartbeatHandle heartbeatHandle;

    /**
     * Creates a reporter with the default settings (see
     * {@link TaskReporterSettings#defaults()}).
     *
     * @param descriptor identity of the execution, required
     * @param transport  transport used to publish events, required
     */
    public TaskReporter(TaskExecutionDescriptor descriptor, TaskTransport transport) {
        this(descriptor, transport, TaskReporterSettings.defaults());
    }

    /**
     * Creates a reporter, emits {@link TaskEventType#STARTED} and, when the
     * settings supply a scheduler and interval, starts the automatic heartbeat.
     *
     * @param descriptor identity of the execution, required
     * @param transport  transport used to publish events, required
     * @param settings   reporter configuration, required
     */
    public TaskReporter(
            TaskExecutionDescriptor descriptor,
            TaskTransport transport,
            TaskReporterSettings settings) {

        Objects.requireNonNull(settings, "settings must not be null");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.clock = settings.clock();
        this.closeBehavior = settings.closeBehavior();
        this.errorHandler = settings.errorHandler();

        emit(TaskEventType.STARTED, null, null);
        this.heartbeatHandle = startHeartbeat(settings.heartbeatScheduler(), settings.heartbeatInterval());
    }

    /**
     * Returns the identity of the execution this reporter is bound to.
     *
     * @return the execution descriptor
     */
    public TaskExecutionDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Emits a progress event.
     *
     * @param percentage completion percentage between {@code 0} and {@code 100}
     * @param message    optional message, may be {@code null}
     */
    public synchronized void progress(int percentage, String message) {
        ensureActive();
        emit(TaskEventType.PROGRESS, message, percentage);
    }

    /**
     * Emits an informational event.
     *
     * @param message the message, may be {@code null}
     */
    public synchronized void info(String message) {
        ensureActive();
        emit(TaskEventType.INFO, message, null);
    }

    /**
     * Emits a warning event.
     *
     * @param message the message, may be {@code null}
     */
    public synchronized void warning(String message) {
        ensureActive();
        emit(TaskEventType.WARNING, message, null);
    }

    /**
     * Emits a heartbeat event signalling that the execution is still alive.
     */
    public synchronized void heartbeat() {
        ensureActive();
        emit(TaskEventType.HEARTBEAT, null, null);
    }

    /**
     * Emits a terminal success event.
     *
     * @param message optional message, may be {@code null}
     */
    public synchronized void completed(String message) {
        emitTerminal(TaskEventType.COMPLETED, message);
    }

    /**
     * Emits a terminal failure event carrying the error message.
     *
     * @param error the cause of the failure, required
     */
    public synchronized void failed(Throwable error) {
        Objects.requireNonNull(error, "error must not be null");
        emitTerminal(TaskEventType.FAILED, error.toString());
    }

    /**
     * Emits a terminal cancellation event.
     *
     * @param message optional message, may be {@code null}
     */
    public synchronized void cancelled(String message) {
        emitTerminal(TaskEventType.CANCELLED, message);
    }

    @Override
    public synchronized void close() {
        if (terminalEmitted || closed) {
            closed = true;
            stopHeartbeat();
            return;
        }

        emitCloseEvent();
        closed = true;
        stopHeartbeat();
    }

    private void onHeartbeatTick() {
        synchronized (this) {
            if (closed || terminalEmitted) {
                return;
            }
            if (emittedSinceLastTick) {
                emittedSinceLastTick = false;
                return;
            }
            emit(TaskEventType.HEARTBEAT, null, null);
        }
    }

    private HeartbeatHandle startHeartbeat(HeartbeatScheduler scheduler, Duration interval) {
        if (scheduler == null && interval == null) {
            return null;
        }

        Objects.requireNonNull(scheduler, "heartbeatScheduler must not be null");
        Objects.requireNonNull(interval, "heartbeatInterval must not be null");
        requirePositive(interval);
        return scheduler.schedule(this::onHeartbeatTick, interval);
    }

    private void emitCloseEvent() {
        if (closeBehavior == CloseBehavior.IGNORE) {
            return;
        }

        emit(closeTerminalType(), CLOSE_WITHOUT_TERMINAL_MESSAGE, null);
        terminalEmitted = true;
    }

    private TaskEventType closeTerminalType() {
        return switch (closeBehavior) {
            case CANCELLED -> TaskEventType.CANCELLED;
            case FAILED -> TaskEventType.FAILED;
            case IGNORE -> throw new IllegalStateException("IGNORE has no terminal type");
        };
    }

    private void emitTerminal(TaskEventType type, String message) {
        ensureActive();
        emit(type, message, null);
        terminalEmitted = true;
        stopHeartbeat();
    }

    private void emit(TaskEventType type, String message, Integer progress) {
        long sequenceNumber = nextSequenceNumber++;

        TaskEvent event = TaskEvent.builder()
                .eventId(descriptor.executionId() + "-" + sequenceNumber)
                .taskName(descriptor.taskName())
                .executionId(descriptor.executionId())
                .correlationKey(descriptor.correlationKey())
                .type(type)
                .timestamp(clock.instant())
                .sequenceNumber(sequenceNumber)
                .message(message)
                .progress(progress)
                .build();

        publish(event);

        if (type != TaskEventType.HEARTBEAT) {
            emittedSinceLastTick = true;
        }
    }

    private void publish(TaskEvent event) {
        try {
            transport.publish(event);
        } catch (RuntimeException error) {
            errorHandler.onPublishFailure(event, error);
        }
    }

    private void stopHeartbeat() {
        if (heartbeatHandle != null) {
            heartbeatHandle.cancel();
            heartbeatHandle = null;
        }
    }

    private void ensureActive() {
        if (terminalEmitted) {
            throw new IllegalStateException(
                    "Execution " + descriptor.executionId()
                            + " already reached a terminal event");
        }
        if (closed) {
            throw new IllegalStateException(
                    "Reporter for execution " + descriptor.executionId() + " is already closed");
        }
    }

    private static void requirePositive(Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException(
                    "heartbeatInterval must be positive: " + interval);
        }
    }
}
