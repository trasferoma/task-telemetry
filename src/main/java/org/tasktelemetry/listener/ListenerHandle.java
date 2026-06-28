package org.tasktelemetry.listener;

/**
 * Handle to a registered listener, used to unregister it.
 */
@FunctionalInterface
public interface ListenerHandle {

    /**
     * Unregisters the listener so it stops receiving events. Calling it more
     * than once has no effect.
     */
    void stop();
}
