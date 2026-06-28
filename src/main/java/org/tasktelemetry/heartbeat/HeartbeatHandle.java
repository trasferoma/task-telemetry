package org.tasktelemetry.heartbeat;

/**
 * Handle to a scheduled heartbeat, used to stop it.
 */
@FunctionalInterface
public interface HeartbeatHandle {

    /**
     * Stops the scheduled heartbeat. Calling it more than once has no effect.
     */
    void cancel();
}
