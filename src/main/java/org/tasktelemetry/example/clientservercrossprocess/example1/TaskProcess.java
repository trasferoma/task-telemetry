package org.tasktelemetry.example.clientservercrossprocess.example1;

import java.time.Duration;

import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskTelemetry;
import org.tasktelemetry.transport.crossprocess.SocketTaskTransport;

/**
 * Task process (producer): connects to the hub and runs a simulated upload,
 * reporting progress. It does not know who is listening; heartbeats during the
 * pauses are produced automatically.
 */
public final class TaskProcess {

    private static final int PROGRESS_STEP = 10;
    private static final Duration STEP_DELAY = Duration.ofSeconds(3);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);

    private TaskProcess() {
    }

    public static void main(String[] args) {
        try (SocketTaskTransport transport = new SocketTaskTransport(ExampleConfig.HOST, ExampleConfig.PORT);
             TaskTelemetry telemetry = TaskTelemetry.builder()
                        .transport(transport)
                        .heartbeatInterval(HEARTBEAT_INTERVAL)
                        .build()) {

            upload(telemetry, "foto.zip");
        }
    }

    private static void upload(TaskTelemetry telemetry, String fileName) {
        try (TaskReporter reporter = telemetry.start(ExampleConfig.TASK_NAME, fileName)) {
            reporter.progress(0, "Upload started");

            for (int percent = PROGRESS_STEP; percent <= 100; percent += PROGRESS_STEP) {
                pause();
                reporter.progress(percent, "Uploaded " + percent + "%");
            }

            reporter.completed("Upload completed: " + fileName);
            System.out.println("Upload finished.");
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
