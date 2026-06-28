package org.tasktelemetry.example.clientserver.example2;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.tasktelemetry.TaskTelemetry;
import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.listener.TaskAwaitTimeoutException;
import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.monitor.TaskHeartbeatMonitor;

/**
 * Client of the {@link TaskUpload} task.
 *
 * <p>It does not receive a {@link TaskTelemetry}: it creates its own over the
 * {@link UploadBus} shared transport. When run, it checks whether an upload is in
 * progress (by waiting a short time for any event from the task); if one is in
 * progress it waits for it to finish, and while waiting it periodically verifies
 * that the task's heart is still beating (status {@code RUNNING}).
 */
public final class UploadClient {

    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration LIVENESS_CHECK_INTERVAL = Duration.ofSeconds(3);
    private static final Duration STALE_AFTER = Duration.ofSeconds(5);
    private static final Duration LOST_AFTER = Duration.ofSeconds(15);

    public void run() {
        TaskHeartbeatMonitor monitor =
                new TaskHeartbeatMonitor(Clock.systemUTC(), STALE_AFTER, LOST_AFTER);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> executionId = new AtomicReference<>();

        try (TaskTelemetry telemetry = newTelemetry()) {
            Optional<ListenerHandle> opt = detectUpload(telemetry, monitor, executionId, finished);

            if (opt.isEmpty()) {
                System.out.println("No upload in progress: the client proceeds with its own work.");
                return;
            }

            ListenerHandle handle = opt.get();

            System.out.println("Upload in progress (execution " + executionId.get()
                    + "): waiting for it to finish...");
            waitForCompletion(monitor, executionId.get(), finished);
            handle.stop();
        }
    }

    private Optional<ListenerHandle> detectUpload(
            TaskTelemetry telemetry,
            TaskHeartbeatMonitor monitor,
            AtomicReference<String> executionId,
            CountDownLatch finished) {

        try {
            ListenerHandle listenerHandle = telemetry.listen()
                    .taskName(TaskUpload.TASK_NAME)
                    .onEvent(event -> onUploadEvent(event, monitor, executionId, finished))
                    .awaitStart(DETECT_TIMEOUT);

            return Optional.of(listenerHandle);
        } catch (TaskAwaitTimeoutException timeout) {
            return Optional.empty();
        }
    }

    private void waitForCompletion(
            TaskHeartbeatMonitor monitor, String executionId, CountDownLatch finished) {

        try {
            while (!finished.await(LIVENESS_CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)) {
                TaskExecutionStatus status = monitor.statusOf(executionId);
                if (status == TaskExecutionStatus.RUNNING) {
                    System.out.println("  ...still uploading, heart is beating (RUNNING)");
                } else if (status == TaskExecutionStatus.STALE) {
                    System.out.println("  !! no heartbeat recently (STALE)");
                } else if (status == TaskExecutionStatus.LOST) {
                    System.out.println("  !! upload looks dead (LOST): giving up");
                    return;
                }
            }
            System.out.println("Upload finished with status: " + monitor.statusOf(executionId));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the upload", interrupted);
        }
    }

    private static void onUploadEvent(
            TaskEvent event,
            TaskHeartbeatMonitor monitor,
            AtomicReference<String> executionId,
            CountDownLatch finished) {

        executionId.compareAndSet(null, event.executionId());
        monitor.onEvent(event);
        printEvent(event);

        if (event.type().isTerminal()) {
            finished.countDown();
        }
    }

    private static void printEvent(TaskEvent event) {
        StringBuilder line = new StringBuilder();
        line.append("[").append(event.type()).append("] #").append(event.sequenceNumber());

        if (event.progress() != null) {
            line.append(" ").append(event.progress()).append("%");
        }
        if (event.message() != null) {
            line.append(" - ").append(event.message());
        }

        System.out.println(line);
    }

    private static TaskTelemetry newTelemetry() {
        return TaskTelemetry.builder()
                .transport(UploadBus.SHARED_TRANSPORT)
                .heartbeatInterval(null)
                .build();
    }
}
