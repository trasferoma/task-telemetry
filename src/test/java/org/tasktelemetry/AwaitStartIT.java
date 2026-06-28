package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.listener.TaskAwaitTimeoutException;

/**
 * End-to-end integration test of the client-side await-with-timeout primitive
 * (SPEC §31) over the real in-memory transport. Same JVM is used to simulate the
 * "client waits for the task" scenario; the primitive is transport-agnostic and
 * will behave the same over a future cross-process transport.
 */
class AwaitStartIT {

    private static final String TASK_NAME = "IMPORT_CLIENTI";

    @Test
    void awaitStartReturnsWhenTaskStartsWithinTimeout() throws InterruptedException {
        List<TaskEvent> received = new CopyOnWriteArrayList<>();

        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .heartbeatInterval(null)
                .executionIdGenerator(() -> "exec-1")
                .build()) {

            Thread task = new Thread(() -> {
                sleepQuietly(Duration.ofMillis(100));
                try (TaskReporter reporter = telemetry.start(TASK_NAME)) {
                    reporter.progress(50, "working");
                    reporter.completed("done");
                }
            });
            task.start();

            ListenerHandle handle = telemetry.listen()
                    .taskName(TASK_NAME)
                    .onEvent(received::add)
                    .awaitStart(Duration.ofSeconds(2));

            task.join();
            handle.stop();

            assertThat(received).extracting(TaskEvent::type).containsExactly(
                    TaskEventType.STARTED, TaskEventType.PROGRESS, TaskEventType.COMPLETED);
        }
    }

    @Test
    void awaitStartThrowsTimeoutWhenTaskNeverStarts() {
        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .heartbeatInterval(null)
                .build()) {

            assertThatExceptionOfType(TaskAwaitTimeoutException.class)
                    .isThrownBy(() -> telemetry.listen()
                            .taskName(TASK_NAME)
                            .onEvent(event -> { })
                            .awaitStart(Duration.ofMillis(150)));
        }
    }

    @Test
    void awaitStartDetectsRunningSilentTaskViaHeartbeat() {
        List<TaskEvent> received = new CopyOnWriteArrayList<>();

        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .heartbeatInterval(Duration.ofMillis(50))
                .executionIdGenerator(() -> "exec-1")
                .build()) {

            try (TaskReporter reporter = telemetry.start(TASK_NAME)) {
                ListenerHandle handle = telemetry.listen()
                        .taskName(TASK_NAME)
                        .onEvent(received::add)
                        .awaitStart(Duration.ofSeconds(2));
                handle.stop();
            }
        }

        assertThat(received).extracting(TaskEvent::type).contains(TaskEventType.HEARTBEAT);
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", interrupted);
        }
    }
}
