package org.tasktelemetry.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Fluent registration of a filtered listener on a {@link TaskTransport}.
 *
 * <p>Filters are optional; those left unset match any value. The listener is
 * registered when {@link #start()} is called, or with {@link #awaitStart(Duration)}
 * which additionally blocks until the first matching event arrives.
 *
 * <pre>{@code
 * ListenerHandle handle = telemetry.listen()
 *         .taskName("IMPORT_CLIENTI")
 *         .onEvent(event -> ...)
 *         .start();
 * }</pre>
 */
public final class ListenerRegistration {

    private final TaskTransport transport;

    private String taskName;
    private String executionId;
    private String correlationKey;
    private TaskEventType eventType;
    private TaskListener delegate;

    public ListenerRegistration(TaskTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    public ListenerRegistration taskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    public ListenerRegistration executionId(String executionId) {
        this.executionId = executionId;
        return this;
    }

    public ListenerRegistration correlationKey(String correlationKey) {
        this.correlationKey = correlationKey;
        return this;
    }

    public ListenerRegistration eventType(TaskEventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public ListenerRegistration onEvent(TaskListener delegate) {
        this.delegate = delegate;
        return this;
    }

    /**
     * Registers the filtered listener and starts delivering matching events.
     *
     * @return a handle to unregister the listener
     */
    public ListenerHandle start() {
        Objects.requireNonNull(delegate, "a listener must be set with onEvent before start");

        TaskListener subscribed = subscribeFiltering(delegate);
        return () -> transport.unsubscribe(subscribed);
    }

    /**
     * Registers the filtered listener, then blocks until the first matching event
     * is delivered or the timeout elapses.
     *
     * <p>The listener starts receiving events immediately, so no event is missed
     * between waiting and listening; this method only gates on the first arrival.
     * A still-running but momentarily silent task is detected through its
     * {@code HEARTBEAT} events, so the timeout should be larger than the configured
     * heartbeat interval.
     *
     * @param timeout the maximum time to wait for the first event, must be positive
     * @return a handle to unregister the listener
     * @throws TaskAwaitTimeoutException if no matching event arrives within the timeout
     */
    public ListenerHandle awaitStart(Duration timeout) {
        Objects.requireNonNull(delegate, "a listener must be set with onEvent before awaitStart");
        requirePositive(timeout);

        CountDownLatch firstEvent = new CountDownLatch(1);
        TaskListener awaitingDelegate = event -> {
            try {
                delegate.onEvent(event);
            } finally {
                firstEvent.countDown();
            }
        };
        TaskListener subscribed = subscribeFiltering(awaitingDelegate);

        if (!awaitFirstEvent(firstEvent, timeout)) {
            transport.unsubscribe(subscribed);
            throw new TaskAwaitTimeoutException(timeoutMessage(timeout));
        }
        return () -> transport.unsubscribe(subscribed);
    }

    private TaskListener subscribeFiltering(TaskListener effectiveDelegate) {
        TaskListener filteringListener =
                new FilteringTaskListener(taskName, executionId, correlationKey, eventType, effectiveDelegate);
        transport.subscribe(filteringListener);
        return filteringListener;
    }

    private String timeoutMessage(Duration timeout) {
        String target = (taskName != null) ? "task '" + taskName + "'" : "any task";
        return "No event from " + target + " received within " + timeout;
    }

    private static boolean awaitFirstEvent(CountDownLatch firstEvent, Duration timeout) {
        try {
            return firstEvent.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting task start", interrupted);
        }
    }

    private static void requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
    }
}
