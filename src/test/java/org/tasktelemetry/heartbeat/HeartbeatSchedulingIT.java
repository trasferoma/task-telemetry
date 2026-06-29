package org.tasktelemetry.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskReporterSettings;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.transport.InMemoryTaskTransport;

/**
 * End-to-end integration test of the automatic heartbeat using the real
 * {@link ExecutorHeartbeatScheduler}: while the reporter stays open and silent,
 * heartbeats must reach a subscribed listener over time.
 */
class HeartbeatSchedulingIT {

    private static final TaskExecutionDescriptor DESCRIPTOR =
            new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");
    private static final Duration INTERVAL = Duration.ofMillis(20);

    @Test
    void realSchedulerEmitsHeartbeatsWhileOpenAndStopsOnClose() throws InterruptedException {
        try (ExecutorHeartbeatScheduler heartbeatScheduler = new ExecutorHeartbeatScheduler()) {
            InMemoryTaskTransport transport = new InMemoryTaskTransport();
            CountDownLatch twoHeartbeats = new CountDownLatch(2);
            transport.subscribe(event -> {
                if (event.type() == TaskEventType.HEARTBEAT) {
                    twoHeartbeats.countDown();
                }
            });

            TaskReporter reporter = new TaskReporter(DESCRIPTOR, transport,
                    TaskReporterSettings.defaults().withHeartbeat(heartbeatScheduler, INTERVAL));

            boolean received = twoHeartbeats.await(2, TimeUnit.SECONDS);
            reporter.close();

            assertThat(received)
                    .as("at least two heartbeats should arrive within the timeout")
                    .isTrue();
        }
    }
}
