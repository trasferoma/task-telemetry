package org.tasktelemetry.transport.crossprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.watch.TaskWatcher;

/**
 * Integration test: verifies end-to-end event delivery from a
 * {@link SocketServerTaskTransport} (task side) to a
 * {@link SocketClientTaskTransport} (consumer side).
 *
 * <p>The server binds an ephemeral port ({@code 0}); the client connects to
 * {@code localhost:server.port()}. Synchronization uses a {@link CountDownLatch}
 * with a bounded timeout — no fixed {@code Thread.sleep} for event-delivery assertions.
 */
class CrossProcessTransportIT {

    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-06-28T10:00:00Z");
    private static final int AWAIT_TIMEOUT_SECONDS = 2;

    @Test
    void clientReceivesEventsPublishedByServer() throws IOException, InterruptedException {
        try (SocketServerTaskTransport server = new SocketServerTaskTransport(0);
             SocketClientTaskTransport client = new SocketClientTaskTransport("localhost", server.port())) {

            int expectedEventCount = 3;
            CountDownLatch latch = new CountDownLatch(expectedEventCount);
            List<TaskEvent> received = new CopyOnWriteArrayList<>();

            client.subscribe(event -> {
                received.add(event);
                latch.countDown();
            });

            // Give the server's accept loop time to register the client connection
            // before the first event is published. A 50 ms bounded wait is sufficient
            // because it is far below the 2-second latch timeout and far above the
            // OS TCP accept + thread-start latency on any CI machine.
            waitForClientConnectionToRegister();

            server.publish(event(0, TaskEventType.STARTED, null));
            server.publish(event(1, TaskEventType.PROGRESS, 50));
            server.publish(event(2, TaskEventType.COMPLETED, 100));

            boolean allReceived = latch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(allReceived)
                    .as("Client should receive all %d events within %ds",
                            expectedEventCount, AWAIT_TIMEOUT_SECONDS)
                    .isTrue();

            assertThat(received).hasSize(3);
            assertThat(received)
                    .extracting(TaskEvent::type)
                    .containsExactly(TaskEventType.STARTED, TaskEventType.PROGRESS, TaskEventType.COMPLETED);
            assertThat(received)
                    .extracting(TaskEvent::progress)
                    .containsExactly(null, 50, 100);
            assertThat(received)
                    .extracting(TaskEvent::sequenceNumber)
                    .containsExactly(0L, 1L, 2L);
            assertThat(received).allSatisfy(e -> {
                assertThat(e.taskName()).isEqualTo("CROSS_PROCESS_TASK");
                assertThat(e.executionId()).isEqualTo("exec-cross-1");
                assertThat(e.correlationKey()).isEqualTo("corr-42");
            });
        }
    }

    @Test
    void multipleClientsAllReceivePublishedEvents() throws IOException, InterruptedException {
        try (SocketServerTaskTransport server = new SocketServerTaskTransport(0);
             SocketClientTaskTransport clientA = new SocketClientTaskTransport("localhost", server.port());
             SocketClientTaskTransport clientB = new SocketClientTaskTransport("localhost", server.port())) {

            CountDownLatch latchA = new CountDownLatch(1);
            CountDownLatch latchB = new CountDownLatch(1);
            List<TaskEvent> receivedA = new CopyOnWriteArrayList<>();
            List<TaskEvent> receivedB = new CopyOnWriteArrayList<>();

            clientA.subscribe(event -> {
                receivedA.add(event);
                latchA.countDown();
            });
            clientB.subscribe(event -> {
                receivedB.add(event);
                latchB.countDown();
            });

            waitForClientConnectionToRegister();

            server.publish(event(0, TaskEventType.INFO, null));

            assertThat(latchA.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(latchB.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(receivedA).hasSize(1);
            assertThat(receivedB).hasSize(1);
            assertThat(receivedA.get(0).type()).isEqualTo(TaskEventType.INFO);
            assertThat(receivedB.get(0).type()).isEqualTo(TaskEventType.INFO);
        }
    }

    @Test
    void publishingWithNoClientConnectedDoesNotThrow() throws IOException {
        try (SocketServerTaskTransport server = new SocketServerTaskTransport(0)) {
            // No client connected — events are discarded silently (best-effort, no replay).
            assertThatCode(() -> {
                server.publish(event(0, TaskEventType.STARTED, null));
                server.publish(event(1, TaskEventType.PROGRESS, 50));
                server.publish(event(2, TaskEventType.COMPLETED, 100));
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void connectingToUnreachableServerThrowsTaskUnreachableException() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        // the probe socket is now closed: nothing is listening on freePort

        assertThatExceptionOfType(TaskUnreachableException.class)
                .isThrownBy(() -> new SocketClientTaskTransport("localhost", freePort));
    }

    @Test
    void clientDetectsLostWhenServerDies() throws IOException, InterruptedException {
        try (SocketServerTaskTransport server = new SocketServerTaskTransport(0);
             SocketClientTaskTransport client =
                     new SocketClientTaskTransport("localhost", server.port());
             TaskWatcher watcher = new TaskWatcher(client, "CROSS_PROCESS_TASK",
                     Duration.ofMillis(200), Duration.ofMillis(500))) {

            // A background producer publishes one event so the watcher detects the
            // running task and captures its execution id.
            Thread producer = new Thread(() -> publishAfterDelay(server));
            producer.start();

            assertThat(watcher.awaitStart(Duration.ofSeconds(2)))
                    .as("the client should detect the running task")
                    .isTrue();
            producer.join();

            // The task dies: stopping the server breaks the connection and no further
            // events (not even heartbeats) reach the client.
            server.close();

            assertThat(watcher.awaitCompletion())
                    .as("after the task dies the client should observe LOST")
                    .isEqualTo(TaskExecutionStatus.LOST);
        }
    }

    // --- helpers ---

    private void publishAfterDelay(SocketServerTaskTransport server) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        server.publish(event(0, TaskEventType.STARTED, null));
    }

    private static TaskEvent event(long sequenceNumber, TaskEventType type, Integer progress) {
        return TaskEvent.builder()
                .eventId("event-" + sequenceNumber)
                .taskName("CROSS_PROCESS_TASK")
                .executionId("exec-cross-1")
                .correlationKey("corr-42")
                .type(type)
                .timestamp(BASE_TIMESTAMP.plusSeconds(sequenceNumber))
                .sequenceNumber(sequenceNumber)
                .progress(progress)
                .build();
    }

    /**
     * Waits briefly for newly-connected clients to be registered by the server's
     * accept loop before the first event is published. This avoids a race where
     * the server has not yet accepted and enqueued the client connection when the
     * first line is written.
     *
     * <p>50 ms is a bounded, deterministic wait: far below the 2-second latch
     * timeout and far above the OS TCP accept + thread-start latency.
     */
    private static void waitForClientConnectionToRegister() throws InterruptedException {
        Thread.sleep(50);
    }
}
