package org.tasktelemetry.watch;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.listener.ListenerRegistration;
import org.tasktelemetry.listener.TaskAwaitTimeoutException;
import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.monitor.TaskHeartbeatMonitor;
import org.tasktelemetry.transport.TaskTransport;

/**
 * High-level helper for consumers that want to follow a single task without
 * dealing with listeners, monitors, latches or execution ids.
 *
 * <p>Typical use: wait for a task to be running, react to its progress, and block
 * until it finishes. Liveness is handled internally: if the task stops emitting
 * (its heart stops), {@link #awaitCompletion()} returns
 * {@link TaskExecutionStatus#LOST} instead of blocking forever, so the consumer
 * never has to check heartbeats itself.
 *
 * <pre>{@code
 * try (TaskWatcher watcher = new TaskWatcher(bus, "FILE_UPLOAD")) {
 *     watcher.onProgress(percent -> progressBar.setValue(percent));
 *     if (!watcher.awaitStart(Duration.ofSeconds(5))) {
 *         return; // no upload running
 *     }
 *     TaskExecutionStatus status = watcher.awaitCompletion();
 * }
 * }</pre>
 */
public final class TaskWatcher implements AutoCloseable {

    private static final Duration DEFAULT_STALE_AFTER = Duration.ofSeconds(5);
    private static final Duration DEFAULT_LOST_AFTER = Duration.ofSeconds(15);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final TaskTransport transport;
    private final String taskName;
    private final TaskHeartbeatMonitor monitor;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicReference<String> executionId = new AtomicReference<>();

    private IntConsumer progressCallback = percent -> {
    };
    private Runnable heartbeatCallback = () -> {
    };
    private ListenerHandle handle;

    public TaskWatcher(TaskTransport transport, String taskName) {
        this(transport, taskName, DEFAULT_STALE_AFTER, DEFAULT_LOST_AFTER);
    }

    public TaskWatcher(TaskTransport transport, String taskName, Duration staleAfter, Duration lostAfter) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.taskName = Objects.requireNonNull(taskName, "taskName must not be null");
        this.monitor = new TaskHeartbeatMonitor(Clock.systemUTC(), staleAfter, lostAfter);
    }

    /**
     * Registers a callback invoked with the percentage of each progress event.
     *
     * @param progressCallback the callback, required
     * @return this watcher
     */
    public TaskWatcher onProgress(IntConsumer progressCallback) {
        this.progressCallback =
                Objects.requireNonNull(progressCallback, "progressCallback must not be null");
        return this;
    }

    /**
     * Registers a callback invoked on every {@code HEARTBEAT} event, kept separate
     * from {@link #onProgress}. The heartbeat carries no payload of its own; it
     * only signals that the task is still alive.
     *
     * @param heartbeatCallback the callback, required
     * @return this watcher
     */
    public TaskWatcher onHeartbeat(Runnable heartbeatCallback) {
        this.heartbeatCallback =
                Objects.requireNonNull(heartbeatCallback, "heartbeatCallback must not be null");
        return this;
    }

    /**
     * Subscribes and blocks until the task emits its first event or the timeout
     * elapses.
     *
     * @param timeout maximum time to wait for the task to show up
     * @return {@code true} if the task is running, {@code false} if it did not
     *         appear within the timeout
     */
    public boolean awaitStart(Duration timeout) {
        try {
            this.handle = new ListenerRegistration(transport)
                    .taskName(taskName)
                    .onEvent(this::onEvent)
                    .awaitStart(timeout);
            return true;
        } catch (TaskAwaitTimeoutException notRunning) {
            return false;
        }
    }

    /**
     * Blocks until the task reaches a terminal event or is considered lost.
     *
     * @return the final status: {@code COMPLETED}, {@code FAILED}, {@code CANCELLED}
     *         or {@code LOST}
     */
    public TaskExecutionStatus awaitCompletion() {
        String execId = executionId.get();
        if (execId == null) {
            throw new IllegalStateException("awaitStart must succeed before awaitCompletion");
        }

        while (true) {
            if (awaitFinished()) {
                return monitor.statusOf(execId);
            }
            if (monitor.statusOf(execId) == TaskExecutionStatus.LOST) {
                return TaskExecutionStatus.LOST;
            }
        }
    }

    @Override
    public void close() {
        if (handle != null) {
            handle.stop();
        }
    }

    private void onEvent(TaskEvent event) {
        executionId.compareAndSet(null, event.executionId());
        monitor.onEvent(event);

        if (event.type() == TaskEventType.HEARTBEAT) {
            heartbeatCallback.run();
        }

        Integer progress = event.progress();
        if (progress != null) {
            progressCallback.accept(progress);
        }
        if (event.type().isTerminal()) {
            finished.countDown();
        }
    }

    private boolean awaitFinished() {
        try {
            return finished.await(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting task completion", interrupted);
        }
    }
}
