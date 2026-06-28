package org.tasktelemetry.example.clientserver.example2;

import java.time.Duration;

import org.tasktelemetry.TaskReporter;

/**
 * Simulated file upload used as a producer example.
 *
 * <p>This is an ordinary class: it only knows how to upload a file and report its
 * progress. It does not build any telemetry runtime, does not know whether it
 * runs on a scheduler or a background thread, and does not know who is listening.
 * It just asks the shared {@link UploadBus#RUNTIME} for a reporter and emits
 * progress. Heartbeats during the pauses are produced automatically.
 *
 * <p>The whole upload takes about 30 seconds.
 */
public final class TaskUpload {

    public static final String TASK_NAME = "FILE_UPLOAD";

    private static final int PROGRESS_STEP = 10;
    private static final Duration STEP_DELAY = Duration.ofSeconds(3);

    public void upload(String fileName) {
        try (TaskReporter reporter = UploadBus.RUNTIME.start(TASK_NAME, fileName)) {
            reporter.progress(0, "Upload started");
            reporter.info("Reading " + fileName);

            for (int percent = PROGRESS_STEP; percent <= 100; percent += PROGRESS_STEP) {
                pause();
                reporter.progress(percent, "Uploaded " + percent + "%");
            }

            reporter.completed("Upload completed: " + fileName);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(STEP_DELAY.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Upload interrupted while waiting", interrupted);
        }
    }
}
