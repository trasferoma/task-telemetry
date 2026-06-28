package org.tasktelemetry.transport;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.listener.TaskListener;

/**
 * In-memory {@link TaskTransport} that delivers each published event
 * synchronously to the currently subscribed listeners, within the same JVM.
 *
 * <p>It performs no serialization and keeps no history: a listener that
 * subscribes after an event was published does not receive that event. Delivery
 * is best-effort and live (see SPEC).
 *
 * <p>The transport is safe for concurrent use. If a listener throws while
 * handling an event, the exception propagates to the caller of {@link #publish};
 * isolating listener failures and applying a publish-failure policy is a concern
 * of the runtime layer, not of this transport.
 */
public final class InMemoryTaskTransport implements TaskTransport {

    private final List<TaskListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(TaskEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        for (TaskListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    @Override
    public void subscribe(TaskListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(TaskListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.remove(listener);
    }
}
