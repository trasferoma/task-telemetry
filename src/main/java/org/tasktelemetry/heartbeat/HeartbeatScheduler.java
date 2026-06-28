package org.tasktelemetry.heartbeat;

import java.time.Duration;

/**
 * Schedules a periodic heartbeat task.
 *
 * <p>This abstraction keeps the reporter free of any threading detail and lets
 * tests drive heartbeat ticks deterministically, without real time (see SPEC).
 */
public interface HeartbeatScheduler {

    /**
     * Schedules the given task to run repeatedly at the given interval.
     *
     * @param heartbeatTask the task to run on every tick, never {@code null}
     * @param interval      the delay between runs, must be positive
     * @return a handle to stop the scheduled task
     */
    HeartbeatHandle schedule(Runnable heartbeatTask, Duration interval);
}
