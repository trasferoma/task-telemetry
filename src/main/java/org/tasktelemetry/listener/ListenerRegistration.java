package org.tasktelemetry.listener;

import java.util.Objects;

import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Fluent registration of a filtered listener on a {@link TaskTransport}.
 *
 * <p>Filters are optional; those left unset match any value. The listener is
 * registered only when {@link #start()} is called.
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

        TaskListener filteringListener =
                new FilteringTaskListener(taskName, executionId, correlationKey, eventType, delegate);
        transport.subscribe(filteringListener);
        return () -> transport.unsubscribe(filteringListener);
    }
}
