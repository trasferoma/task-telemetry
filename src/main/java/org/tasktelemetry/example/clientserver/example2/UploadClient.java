package org.tasktelemetry.example.clientserver.example2;

import java.time.Duration;

import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.watch.TaskWatcher;

/**
 * Client of the {@link TaskUpload} task.
 *
 * <p>Its real job is to show the upload progress, not to deal with telemetry
 * plumbing. With {@link TaskWatcher} it only needs to: react to progress, wait
 * for the upload to be running, and wait for it to finish. Liveness (is the heart
 * still beating?) is handled by the watcher, which returns {@code LOST} if the
 * upload dies.
 */
public final class UploadClient {

    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(5);

    public void run() {
        try (TaskWatcher watcher = new TaskWatcher(UploadBus.SHARED_TRANSPORT, TaskUpload.TASK_NAME)) {
            watcher.onProgress(percent -> System.out.println("  upload at " + percent + "%"));

            if (!watcher.awaitStart(DETECT_TIMEOUT)) {
                System.out.println("No upload in progress: the client proceeds with its own work.");
                return;
            }

            System.out.println("Upload in progress: waiting for it to finish...");
            TaskExecutionStatus status = watcher.awaitCompletion();
            System.out.println("Upload finished with status: " + status);
        }
    }
}
