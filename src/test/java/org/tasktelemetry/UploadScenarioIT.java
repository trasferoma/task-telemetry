package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.transport.inmemory.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;
import org.tasktelemetry.watch.TaskWatcher;

/**
 * Integration test of the example2 scenario (SPEC §31) using the high-level
 * {@link TaskWatcher}, with short timings. A client created independently from
 * the producer detects a running upload over a shared transport, follows its
 * progress and waits for it to finish. It also covers the edge cases: no upload
 * running, and an upload that dies silently (reported as {@code LOST}).
 */
class UploadScenarioIT {

    private static final String TASK_NAME = "FILE_UPLOAD";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(100);
    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(2);

    private final TaskTransport bus = new InMemoryTaskTransport();

    @Test
    void clientFollowsRunningUploadUntilCompleted() throws InterruptedException {
        Thread uploadProcess = new Thread(this::runFastUpload, "upload-process");
        List<Integer> progressSeen = new CopyOnWriteArrayList<>();

        uploadProcess.start();

        try (TaskWatcher watcher =
                new TaskWatcher(bus, TASK_NAME, Duration.ofMillis(300), Duration.ofSeconds(1))) {
            watcher.onProgress(progressSeen::add);

            assertThat(watcher.awaitStart(DETECT_TIMEOUT)).isTrue();
            assertThat(watcher.awaitCompletion()).isEqualTo(TaskExecutionStatus.COMPLETED);
            assertThat(progressSeen).contains(100);
        }

        uploadProcess.join();
    }

    @Test
    void clientFindsNoUploadWhenNoneIsRunning() {
        try (TaskWatcher watcher = new TaskWatcher(bus, TASK_NAME)) {
            assertThat(watcher.awaitStart(Duration.ofMillis(200))).isFalse();
        }
    }

    @Test
    void clientReportsLostWhenUploadDiesSilently() {
        TaskTelemetry producer = TaskTelemetry.builder()
                .transport(bus)
                .heartbeatInterval(HEARTBEAT_INTERVAL)
                .build();
        TaskReporter reporter = producer.start(TASK_NAME, "foto.zip");
        reporter.progress(10, "Uploaded 10%");

        try (TaskWatcher watcher =
                new TaskWatcher(bus, TASK_NAME, Duration.ofMillis(200), Duration.ofMillis(500))) {
            assertThat(watcher.awaitStart(DETECT_TIMEOUT)).isTrue();

            // the upload dies silently: heartbeats stop, no terminal event is ever sent
            producer.close();

            assertThat(watcher.awaitCompletion()).isEqualTo(TaskExecutionStatus.LOST);
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

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", interrupted);
        }
    }
}
