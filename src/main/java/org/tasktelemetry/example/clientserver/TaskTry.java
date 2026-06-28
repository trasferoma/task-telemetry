package org.tasktelemetry.example.clientserver;

import java.time.Duration;
import java.util.Objects;

import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskTelemetry;

/**
 * Example task (producer): it runs some work and emits telemetry through a
 * {@link TaskReporter}, without knowing who is listening.
 *
 * <p>It pauses between steps so that, while it stays silent, the automatic
 * heartbeat fires and can be observed by a listener.
 */
public final class TaskTry {

            public static final String TASK_NAME = "TASK_TRY";

            private static final Duration STEP_DELAY = Duration.ofSeconds(3);

            private final TaskTelemetry telemetry;

    public TaskTry(TaskTelemetry telemetry) {
                this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
            }

            public void run() {
                try (TaskReporter reporter = telemetry.start(TASK_NAME)) {
            reporter.progress(0, "Task started");
            pause();
            reporter.info("Doing some work");
            pause();
            reporter.progress(50, "Halfway through");
            pause();
            reporter.progress(100, "Work done");
            reporter.completed("Task completed");
        }
    }

    private static void pause() {
        try {
            Thread.sleep(STEP_DELAY.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task interrupted while waiting", interrupted);
        }
    }
}
