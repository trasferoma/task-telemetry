package org.tasktelemetry;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.heartbeat.ExecutorHeartbeatScheduler;
import org.tasktelemetry.heartbeat.HeartbeatScheduler;
import org.tasktelemetry.listener.ListenerRegistration;
import org.tasktelemetry.logging.JulTaskTelemetryLogger;
import org.tasktelemetry.transport.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Entry point of the library: configures the runtime and acts as a factory for
 * {@link TaskReporter} instances and listener registrations.
 *
 * <p>It is configured through {@link #builder()} (or {@link #defaults()}); there
 * are no configuration files and no annotations (see SPEC). Each call to
 * {@link #start(String)} begins a new execution with its own generated
 * {@code executionId} and wires a reporter with the shared transport, clock,
 * close behavior and heartbeat.
 *
 * <p>If the runtime created its own heartbeat scheduler (the default), call
 * {@link #close()} to release it. A scheduler supplied by the caller is left
 * untouched.
 */
public final class TaskTelemetry implements AutoCloseable {

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(5);

    private final TaskTransport transport;
    private final Clock clock;
    private final TaskReporter.CloseBehavior closeBehavior;
    private final HeartbeatScheduler heartbeatScheduler;
    private final Duration heartbeatInterval;
    private final ExecutorHeartbeatScheduler ownedScheduler;
    private final Supplier<String> executionIdGenerator;
    private final TaskTelemetryErrorHandler errorHandler;

    private TaskTelemetry(
            TaskTransport transport,
            Clock clock,
            TaskReporter.CloseBehavior closeBehavior,
            HeartbeatScheduler heartbeatScheduler,
            Duration heartbeatInterval,
            ExecutorHeartbeatScheduler ownedScheduler,
            Supplier<String> executionIdGenerator,
            TaskTelemetryErrorHandler errorHandler) {

        this.transport = transport;
        this.clock = clock;
        this.closeBehavior = closeBehavior;
        this.heartbeatScheduler = heartbeatScheduler;
        this.heartbeatInterval = heartbeatInterval;
        this.ownedScheduler = ownedScheduler;
        this.executionIdGenerator = executionIdGenerator;
        this.errorHandler = errorHandler;
    }

    /**
     * Creates a runtime with all default settings: in-memory transport, system
     * UTC clock, {@link TaskReporter.CloseBehavior#CANCELLED} and an automatic
     * heartbeat every five seconds.
     *
     * @return a ready-to-use runtime
     */
    public static TaskTelemetry defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts a new execution and returns its reporter, which has already emitted
     * {@link TaskEventType#STARTED}.
     *
     * @param taskName type of task, required
     * @return the reporter bound to the new execution
     */
    public TaskReporter start(String taskName) {
        return start(taskName, null);
    }

    /**
     * Starts a new execution linked to an application domain.
     *
     * @param taskName       type of task, required
     * @param correlationKey optional link to an application domain, may be {@code null}
     * @return the reporter bound to the new execution
     */
    public TaskReporter start(String taskName, String correlationKey) {
        String executionId = executionIdGenerator.get();
        TaskExecutionDescriptor descriptor =
                new TaskExecutionDescriptor(taskName, executionId, correlationKey);

        return new TaskReporter(
                descriptor, transport, clock, closeBehavior,
                heartbeatScheduler, heartbeatInterval, errorHandler);
    }

    /**
     * Begins the registration of a filtered listener.
     *
     * @return a registration builder
     */
    public ListenerRegistration listen() {
        return new ListenerRegistration(transport);
    }

    @Override
    public void close() {
        if (ownedScheduler != null) {
            ownedScheduler.close();
        }
    }

    /**
     * Builder for {@link TaskTelemetry}. Unset settings fall back to sensible
     * defaults; a {@code null} heartbeat interval disables the heartbeat.
     */
    public static final class Builder {

        private TaskTransport transport;
        private Clock clock = Clock.systemUTC();
        private TaskReporter.CloseBehavior closeBehavior = TaskReporter.CloseBehavior.CANCELLED;
        private HeartbeatScheduler heartbeatScheduler;
        private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        private Supplier<String> executionIdGenerator = () -> UUID.randomUUID().toString();
        private String logPrefix = JulTaskTelemetryLogger.DEFAULT_LOG_PREFIX;
        private TaskTelemetryErrorHandler errorHandler;

        private Builder() {
        }

        public Builder transport(TaskTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        public Builder closeBehavior(TaskReporter.CloseBehavior closeBehavior) {
            this.closeBehavior =
                    Objects.requireNonNull(closeBehavior, "closeBehavior must not be null");
            return this;
        }

        public Builder heartbeatScheduler(HeartbeatScheduler heartbeatScheduler) {
            this.heartbeatScheduler = heartbeatScheduler;
            return this;
        }

        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public Builder executionIdGenerator(Supplier<String> executionIdGenerator) {
            this.executionIdGenerator =
                    Objects.requireNonNull(executionIdGenerator, "executionIdGenerator must not be null");
            return this;
        }

        /**
         * Sets the prefix prepended to every log message emitted by the default
         * logging error handler. Pass an empty string for no prefix. Ignored when
         * a custom {@link #errorHandler(TaskTelemetryErrorHandler)} is supplied.
         *
         * @param logPrefix the marker prepended to every log message, required
         * @return this builder
         */
        public Builder logPrefix(String logPrefix) {
            this.logPrefix = Objects.requireNonNull(logPrefix, "logPrefix must not be null");
            return this;
        }

        public Builder errorHandler(TaskTelemetryErrorHandler errorHandler) {
            this.errorHandler =
                    Objects.requireNonNull(errorHandler, "errorHandler must not be null");
            return this;
        }

        public TaskTelemetry build() {
            TaskTransport resolvedTransport =
                    (transport != null) ? transport : new InMemoryTaskTransport();

            boolean heartbeatEnabled = heartbeatInterval != null;
            ExecutorHeartbeatScheduler ownedScheduler = resolveOwnedScheduler(heartbeatEnabled);
            HeartbeatScheduler resolvedScheduler = resolveScheduler(heartbeatEnabled, ownedScheduler);
            Duration resolvedInterval = heartbeatEnabled ? heartbeatInterval : null;
            TaskTelemetryErrorHandler resolvedErrorHandler = resolveErrorHandler();

            return new TaskTelemetry(
                    resolvedTransport,
                    clock,
                    closeBehavior,
                    resolvedScheduler,
                    resolvedInterval,
                    ownedScheduler,
                    executionIdGenerator,
                    resolvedErrorHandler);
        }

        private TaskTelemetryErrorHandler resolveErrorHandler() {
            return Objects.requireNonNullElseGet(errorHandler, () -> TaskTelemetryErrorHandler.logging(logPrefix));
        }

        private ExecutorHeartbeatScheduler resolveOwnedScheduler(boolean heartbeatEnabled) {
            boolean mustCreateScheduler = heartbeatEnabled && heartbeatScheduler == null;
            return mustCreateScheduler ? new ExecutorHeartbeatScheduler() : null;
        }

        private HeartbeatScheduler resolveScheduler(
                boolean heartbeatEnabled, ExecutorHeartbeatScheduler ownedScheduler) {

            if (!heartbeatEnabled) {
                return null;
            }
            return (heartbeatScheduler != null) ? heartbeatScheduler : ownedScheduler;
        }
    }
}
