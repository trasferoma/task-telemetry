package org.tasktelemetry.heartbeat;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link HeartbeatScheduler} backed by a shared {@link ScheduledExecutorService}
 * that uses daemon threads.
 *
 * <p>A single instance is meant to be shared across the reporters of one
 * telemetry runtime (see SPEC). Call {@link #close()} to stop the underlying
 * threads.
 */
public final class ExecutorHeartbeatScheduler implements HeartbeatScheduler, AutoCloseable {

    private static final String THREAD_NAME_PREFIX = "task-telemetry-heartbeat-";

    private final ScheduledExecutorService scheduler;

    public ExecutorHeartbeatScheduler() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
    }

    @Override
    public HeartbeatHandle schedule(Runnable heartbeatTask, Duration interval) {
        long intervalMillis = interval.toMillis();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                heartbeatTask, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
