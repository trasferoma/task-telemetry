package org.tasktelemetry.listener;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.transport.inmemory.InMemoryTaskTransport;

class ListenerRegistrationAwaitTest {

    private final InMemoryTaskTransport transport = new InMemoryTaskTransport();

    @Test
    void requiresListenerBeforeAwait() {
        ListenerRegistration registration = new ListenerRegistration(transport).taskName("IMPORT_CLIENTI");

        assertThatNullPointerException()
                .isThrownBy(() -> registration.awaitStart(Duration.ofSeconds(1)));
    }

    @Test
    void rejectsNullTimeout() {
        ListenerRegistration registration =
                new ListenerRegistration(transport).onEvent(event -> { });

        assertThatNullPointerException().isThrownBy(() -> registration.awaitStart(null));
    }

    @Test
    void rejectsNonPositiveTimeout() {
        ListenerRegistration registration =
                new ListenerRegistration(transport).onEvent(event -> { });

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registration.awaitStart(Duration.ZERO));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registration.awaitStart(Duration.ofSeconds(-1)));
    }
}
