package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.listener.TaskAwaitTimeoutException;
import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.monitor.TaskHeartbeatMonitor;
import org.tasktelemetry.transport.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Integration test of the example2 scenario (SPEC §31), with short timings: a
 * client created independently from the producer detects a running upload over a
 * shared transport, waits for it to finish, and meanwhile checks that the task's
 * heart keeps beating. It also covers the edge cases: no upload running, and an
 * upload that dies silently.
 */
class UploadScenarioIT {

    private static final String TASK_NAME = "FILE_UPLOAD";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(100);
    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(2);

    private final TaskTransport bus = new InMemoryTaskTransport();

    @Test
    void clientDetectsRunningUploadAndWaitsUntilCompleted() throws InterruptedException {
        Thread uploadProcess = new Thread(this::runFastUpload, "upload-process");

        TaskHeartbeatMonitor monitor = new TaskHeartbeatMonitor(
                Clock.systemUTC(), Duration.ofMillis(300), Duration.ofSeconds(1));
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> executionId = new AtomicReference<>();

        uploadProcess.start();

        try (TaskTelemetry client = clientTelemetry()) {
            ListenerHandle handle = client.listen()
                    .taskName(TASK_NAME)
                    .onEvent(event -> {
                        executionId.compareAndSet(null, event.executionId());
                        monitor.onEvent(event);
                        if (event.type().isTerminal()) {
                            finished.countDown();
                        }
                    })
                    .awaitStart(DETECT_TIMEOUT);

            boolean running = false;
            boolean completed = false;
            for (int attempt = 0; attempt < 50 && !completed; attempt++) {
                completed = finished.await(100, TimeUnit.MILLISECONDS);
                if (!completed && monitor.statusOf(executionId.get()) == TaskExecutionStatus.RUNNING) {
                    running = true;
                }
            }
            handle.stop();

            assertThat(running).as("the client should see the heart beating").isTrue();
            assertThat(completed).as("the upload should complete").isTrue();
            assertThat(monitor.statusOf(executionId.get())).isEqualTo(TaskExecutionStatus.COMPLETED);
        }

        uploadProcess.join();
    }

    @Test
    void clientFindsNoUploadWhenNoneIsRunning() {
        try (TaskTelemetry client = clientTelemetry()) {
            assertThatExceptionOfType(TaskAwaitTimeoutException.class).isThrownBy(() -> client.listen()
                    .taskName(TASK_NAME)
                    .onEvent(event -> { })
                    .awaitStart(Duration.ofMillis(200)));
        }
    }

    @Test
    void clientDetectsLostUploadWhenItDiesSilently() throws InterruptedException {
        TaskHeartbeatMonitor monitor = new TaskHeartbeatMonitor(
                Clock.systemUTC(), Duration.ofMillis(200), Duration.ofMillis(500));
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> executionId = new AtomicReference<>();

        // producer alive: emits STARTED and progress, plus heartbeats every 100ms
        TaskTelemetry producer = TaskTelemetry.builder()
                .transport(bus)
                .heartbeatInterval(HEARTBEAT_INTERVAL)
                .build();
        TaskReporter reporter = producer.start(TASK_NAME, "foto.zip");
        reporter.progress(10, "Uploaded 10%");

        try (TaskTelemetry client = clientTelemetry()) {
            ListenerHandle handle = client.listen()
                    .taskName(TASK_NAME)
                    .onEvent(event -> {
                        executionId.compareAndSet(null, event.executionId());
                        monitor.onEvent(event);
                        if (event.type().isTerminal()) {
                            finished.countDown();
                        }
                    })
                    .awaitStart(DETECT_TIMEOUT);

            // the upload dies silently: heartbeats stop, no terminal event is ever sent
            producer.close();

            boolean terminated = finished.await(900, TimeUnit.MILLISECONDS);
            handle.stop();

            assertThat(terminated).as("a dead upload never sends a terminal event").isFalse();
            assertThat(monitor.statusOf(executionId.get())).isEqualTo(TaskExecutionStatus.LOST);
        }

        reporter.close();
    }

    private void runFastUpload() {
        try (TaskTelemetry producer = TaskTelemetry.builder()
                .transport(bus)
                .heartbeatInterval(HEARTBEAT_INTERVAL)
                .build();
                TaskReporter reporter = producer.start(TASK_NAME, "foto.zip")) {

            for (int percent = 0; percent <= 100; percent += 25) {
                reporter.progress(percent, "Uploaded " + percent + "%");
                sleep(Duration.ofMillis(150));
            }
            reporter.completed("Upload completed");
        }
    }

    private TaskTelemetry clientTelemetry() {
        return TaskTelemetry.builder()
                .transport(bus)
                .heartbeatInterval(null)
                .build();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", interrupted);
        }
    }
}
