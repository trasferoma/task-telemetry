package org.tasktelemetry.transport.crossprocess;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;

/**
 * Integration test: verifies end-to-end event delivery between two
 * {@link SocketTaskTransport} instances connected through a shared
 * {@link CrossProcessTaskHub}.
 *
 * <p>One transport acts as producer (publishes events), the other as consumer
 * (has a listener collecting received events). Synchronization uses a
 * {@link CountDownLatch} with a bounded timeout — no fixed {@code Thread.sleep}.
 */
class CrossProcessTransportIT {

    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-06-28T10:00:00Z");
    private static final int AWAIT_TIMEOUT_SECONDS = 2;

    @Test
    void consumerReceivesEventsPublishedByProducer() throws IOException, InterruptedException {
        try (CrossProcessTaskHub hub = new CrossProcessTaskHub(0)) {
            int port = hub.port();

            try (SocketTaskTransport producer = new SocketTaskTransport("localhost", port);
                 SocketTaskTransport consumer = new SocketTaskTransport("localhost", port)) {

                int expectedEventCount = 3;
                CountDownLatch latch = new CountDownLatch(expectedEventCount);
                List<TaskEvent> received = new CopyOnWriteArrayList<>();

                consumer.subscribe(event -> {
                    received.add(event);
                    latch.countDown();
                });

                // Give the consumer's reader thread a moment to be ready
                waitForReaderThreadsToConnect();

                producer.publish(event(0, TaskEventType.STARTED, null));
                producer.publish(event(1, TaskEventType.PROGRESS, 50));
                producer.publish(event(2, TaskEventType.COMPLETED, 100));

                boolean allReceived = latch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertThat(allReceived)
                        .as("Consumer should receive all %d events within %ds",
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
    }

    @Test
    void producerDoesNotReceiveItsOwnPublishedEvents() throws IOException, InterruptedException {
        try (CrossProcessTaskHub hub = new CrossProcessTaskHub(0)) {
            int port = hub.port();

            try (SocketTaskTransport producer = new SocketTaskTransport("localhost", port);
                 SocketTaskTransport consumer = new SocketTaskTransport("localhost", port)) {

                List<TaskEvent> producerReceived = new CopyOnWriteArrayList<>();
                List<TaskEvent> consumerReceived = new CopyOnWriteArrayList<>();
                CountDownLatch consumerLatch = new CountDownLatch(1);

                producer.subscribe(producerReceived::add);
                consumer.subscribe(event -> {
                    consumerReceived.add(event);
                    consumerLatch.countDown();
                });

                waitForReaderThreadsToConnect();

                producer.publish(event(0, TaskEventType.STARTED, null));

                boolean consumerGotEvent = consumerLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertThat(consumerGotEvent).isTrue();
                assertThat(consumerReceived).hasSize(1);
                // The producer must not receive its own message (hub relays to others only)
                assertThat(producerReceived).isEmpty();
            }
        }
    }

    @Test
    void multipleConsumersAllReceivePublishedEvents() throws IOException, InterruptedException {
        try (CrossProcessTaskHub hub = new CrossProcessTaskHub(0)) {
            int port = hub.port();

            try (SocketTaskTransport producer = new SocketTaskTransport("localhost", port);
                 SocketTaskTransport consumerA = new SocketTaskTransport("localhost", port);
                 SocketTaskTransport consumerB = new SocketTaskTransport("localhost", port)) {

                CountDownLatch latchA = new CountDownLatch(1);
                CountDownLatch latchB = new CountDownLatch(1);
                List<TaskEvent> receivedA = new CopyOnWriteArrayList<>();
                List<TaskEvent> receivedB = new CopyOnWriteArrayList<>();

                consumerA.subscribe(event -> { receivedA.add(event); latchA.countDown(); });
                consumerB.subscribe(event -> { receivedB.add(event); latchB.countDown(); });

                waitForReaderThreadsToConnect();

                producer.publish(event(0, TaskEventType.INFO, null));

                assertThat(latchA.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(latchB.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

                assertThat(receivedA).hasSize(1);
                assertThat(receivedB).hasSize(1);
                assertThat(receivedA.get(0).type()).isEqualTo(TaskEventType.INFO);
                assertThat(receivedB.get(0).type()).isEqualTo(TaskEventType.INFO);
            }
        }
    }

    // --- helpers ---

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
     * Waits briefly for newly-connected reader threads to register with the hub
     * before the producer starts publishing. This avoids a race where the hub
     * has not yet processed the accept/register of a new connection when the
     * first event arrives.
     *
     * <p>We use a short, bounded sleep here only because there is no observable
     * application event that signals "reader thread is ready and registered".
     * 50 ms is far below the 2-second latch timeout and far above the OS
     * TCP accept + thread-start latency on any CI machine.
     */
    private static void waitForReaderThreadsToConnect() throws InterruptedException {
        Thread.sleep(50);
    }
}
