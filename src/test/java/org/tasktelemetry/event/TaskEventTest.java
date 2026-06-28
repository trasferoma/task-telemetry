package org.tasktelemetry.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;

import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(InstancioExtension.class)
class TaskEventTest {

    private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-06-28T10:15:30Z");

    @Test
    void builder_populatesAllFields() {
        Object payload = Instancio.create(SamplePayload.class);

        TaskEvent event = TaskEvent.builder()
                .eventId("event-1")
                .taskName("IMPORT_CLIENTI")
                .executionId("exec-1")
                .correlationKey("pratica-556101")
                .type(TaskEventType.PROGRESS)
                .timestamp(FIXED_TIMESTAMP)
                .sequenceNumber(7)
                .message("Half of the file processed")
                .progress(50)
                .payload(payload)
                .build();

        assertThat(event.eventId()).isEqualTo("event-1");
        assertThat(event.taskName()).isEqualTo("IMPORT_CLIENTI");
        assertThat(event.executionId()).isEqualTo("exec-1");
        assertThat(event.correlationKey()).isEqualTo("pratica-556101");
        assertThat(event.type()).isEqualTo(TaskEventType.PROGRESS);
        assertThat(event.timestamp()).isEqualTo(FIXED_TIMESTAMP);
        assertThat(event.sequenceNumber()).isEqualTo(7);
        assertThat(event.message()).isEqualTo("Half of the file processed");
        assertThat(event.progress()).isEqualTo(50);
        assertThat(event.payload()).isSameAs(payload);
    }

    @Test
    void builder_leavesOptionalFieldsNull() {
        TaskEvent event = minimalBuilder().build();

        assertThat(event.correlationKey()).isNull();
        assertThat(event.message()).isNull();
        assertThat(event.progress()).isNull();
        assertThat(event.payload()).isNull();
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        TaskEvent first = minimalBuilder().build();
        TaskEvent second = minimalBuilder().build();

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankEventId(String invalidEventId) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> minimalBuilder().eventId(invalidEventId).build())
                .withMessageContaining("eventId");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankTaskName(String invalidTaskName) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> minimalBuilder().taskName(invalidTaskName).build())
                .withMessageContaining("taskName");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankExecutionId(String invalidExecutionId) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> minimalBuilder().executionId(invalidExecutionId).build())
                .withMessageContaining("executionId");
    }

    @Test
    void rejectsNullType() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> minimalBuilder().type(null).build())
                .withMessageContaining("type");
    }

    @Test
    void rejectsNullTimestamp() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> minimalBuilder().timestamp(null).build())
                .withMessageContaining("timestamp");
    }

    @Test
    void rejectsNegativeSequenceNumber() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> minimalBuilder().sequenceNumber(-1).build())
                .withMessageContaining("sequenceNumber");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101, 200})
    void rejectsProgressOutOfRange(int invalidProgress) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> minimalBuilder().progress(invalidProgress).build())
                .withMessageContaining("progress");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 50, 100})
    void acceptsProgressWithinRange(int validProgress) {
        assertThatNoException()
                .isThrownBy(() -> minimalBuilder().progress(validProgress).build());
    }

    private static TaskEvent.Builder minimalBuilder() {
        return TaskEvent.builder()
                .eventId("event-1")
                .taskName("IMPORT_CLIENTI")
                .executionId("exec-1")
                .type(TaskEventType.STARTED)
                .timestamp(FIXED_TIMESTAMP)
                .sequenceNumber(0);
    }

    record SamplePayload(String label, int value) {
    }
}
