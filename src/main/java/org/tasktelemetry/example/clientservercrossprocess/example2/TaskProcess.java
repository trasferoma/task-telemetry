package org.tasktelemetry.example.clientservercrossprocess.example2;

import java.io.IOException;
import java.time.Duration;

import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskTelemetry;
import org.tasktelemetry.transport.crossprocess.SocketServerTaskTransport;

/**
 * Task process (producer and server): binds the port and runs a simulated upload,
 * reporting progress. It does not know who is listening; heartbeats during the
 * pauses are produced automatically. Start this process <em>before</em> the client.
 */
public final class TaskProcess {

    private static final int PROGRESS_STEP = 10;
    private static final Duration STEP_DELAY = Duration.ofSeconds(3);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);

    private TaskProcess() {
    }

    public static void main(String[] args) throws IOException {
        try (SocketServerTaskTransport transport = new SocketServerTaskTransport(ExampleConfig.PORT);
             TaskTelemetry telemetry = TaskTelemetry.builder()
                        .transport(transport)
                        .heartbeatInterval(HEARTBEAT_INTERVAL)
                        .build()) {

            System.out.println("Task server listening on "
                    + ExampleConfig.HOST + ":" + transport.port()
                    + " - run the snapshot client now");
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
