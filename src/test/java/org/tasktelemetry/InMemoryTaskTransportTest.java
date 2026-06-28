package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class InMemoryTaskTransportTest {

    @Test
    void subscribedListenerReceivesPublishedEvents() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();
        List<TaskEvent> received = new ArrayList<>();
        transport.subscribe(received::add);

        transport.publish(event(0, TaskEventType.STARTED));
        transport.publish(event(1, TaskEventType.COMPLETED));

        assertThat(received)
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.COMPLETED);
    }

    @Test
    void everySubscribedListenerReceivesTheEvent() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();
        List<TaskEvent> firstInbox = new ArrayList<>();
        List<TaskEvent> secondInbox = new ArrayList<>();
        transport.subscribe(firstInbox::add);
        transport.subscribe(secondInbox::add);

        transport.publish(event(0, TaskEventType.STARTED));

        assertThat(firstInbox).hasSize(1);
        assertThat(secondInbox).hasSize(1);
    }

    @Test
    void unsubscribedListenerStopsReceivingEvents() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();
        List<TaskEvent> received = new ArrayList<>();
        TaskListener listener = received::add;
        transport.subscribe(listener);

        transport.publish(event(0, TaskEventType.STARTED));
        transport.unsubscribe(listener);
        transport.publish(event(1, TaskEventType.COMPLETED));

        assertThat(received)
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED);
    }

    @Test
    void lateSubscriberDoesNotReceivePastEvents() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();

        transport.publish(event(0, TaskEventType.STARTED));
        List<TaskEvent> received = new ArrayList<>();
        transport.subscribe(received::add);

        assertThat(received).isEmpty();
    }

    @Test
    void publishPropagatesListenerException() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();
        transport.subscribe(received -> {
            throw new IllegalStateException("listener failed");
        });

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> transport.publish(event(0, TaskEventType.STARTED)))
                .withMessageContaining("listener failed");
    }

    @Test
    void rejectsNullArguments() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();

        assertThatNullPointerException().isThrownBy(() -> transport.publish(null));
        assertThatNullPointerException().isThrownBy(() -> transport.subscribe(null));
        assertThatNullPointerException().isThrownBy(() -> transport.unsubscribe(null));
    }

    private static TaskEvent event(long sequenceNumber, TaskEventType type) {
        return TaskEvent.builder()
                .eventId("exec-1-" + sequenceNumber)
                .taskName("IMPORT_CLIENTI")
                .executionId("exec-1")
                .type(type)
                .timestamp(Instant.parse("2026-06-28T10:00:00Z").plusSeconds(sequenceNumber))
                .sequenceNumber(sequenceNumber)
                .build();
    }
}
