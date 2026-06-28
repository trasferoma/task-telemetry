package org.tasktelemetry.example.clientserver.example2;

import java.time.Duration;

import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskTelemetry;

/**
 * Simulated file upload used as a producer example.
 *
 * <p>This is an ordinary class: it only knows how to upload a file and report its
 * progress. It does not know whether it runs on a scheduler, on a background
 * thread or in the foreground, and it does not know who (if anyone) is listening.
 * It never emits heartbeats: those are produced automatically by the runtime
 * while the reporter is open and the upload is silent between steps.
 *
 * <p>It creates its own {@link TaskTelemetry} over the {@link UploadBus} shared
 * transport, so a separately-created client can observe its events. The whole
 * upload takes about 30 seconds: a {@code STARTED} event, progress from 0% to
 * 100% in 10% steps with a pause between each, heartbeats during the pauses, and
 * a final {@code COMPLETED} event.
 */
public final class TaskUpload {

    public static final String TASK_NAME = "FILE_UPLOAD";

    private static final int PROGRESS_STEP = 10;
    private static final Duration STEP_DELAY = Duration.ofSeconds(3);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);

    public void upload(String fileName) {
        try (TaskTelemetry telemetry = newTelemetry();
                TaskReporter reporter = telemetry.start(TASK_NAME, fileName)) {

            reporter.progress(0, "Upload started");
            reporter.info("Reading " + fileName);

            for (int percent = PROGRESS_STEP; percent <= 100; percent += PROGRESS_STEP) {
                pause();
                reporter.progress(percent, "Uploaded " + percent + "%");
            }

            reporter.completed("Upload completed: " + fileName);
        }
    }

    private static TaskTelemetry newTelemetry() {
        return TaskTelemetry.builder()
                .transport(UploadBus.SHARED_TRANSPORT)
                .heartbeatInterval(HEARTBEAT_INTERVAL)
                .build();
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
