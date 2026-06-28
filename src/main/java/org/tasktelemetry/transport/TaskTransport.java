package org.tasktelemetry.transport;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.listener.TaskListener;

/**
 * Pluggable delivery channel between event emitters and listeners.
 *
 * <p>The core does not know whether the underlying mechanism is in-memory, a
 * local socket or a remote broker. An implementation delivers each published
 * event to the listeners currently subscribed.
 *
 * <p>Delivery is best-effort and live (see SPEC): events published while no
 * listener is subscribed may be lost, and past events are not replayed to
 * listeners that subscribe later.
 */
public interface TaskTransport {

    /**
     * Publishes an event to the subscribed listeners.
     *
     * @param event the event to deliver, never {@code null}
     */
    void publish(TaskEvent event);

    /**
     * Registers a listener so it starts receiving published events.
     *
     * @param listener the listener to register, never {@code null}
     */
    void subscribe(TaskListener listener);

    /**
     * Removes a previously registered listener so it stops receiving events.
     * Listeners that are not currently subscribed are ignored.
     *
     * @param listener the listener to remove, never {@code null}
     */
    void unsubscribe(TaskListener listener);
}
