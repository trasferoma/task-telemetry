package org.tasktelemetry.transport.crossprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;

class TextTaskEventSerializerTest {

    private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-06-28T10:00:00Z");

    private final TextTaskEventSerializer serializer = new TextTaskEventSerializer();

    @Test
    void roundTrip_fullyPopulatedEvent() {
        TaskEvent original = TaskEvent.builder()
                .eventId("event-abc-123")
                .taskName("IMPORT_CLIENTI")
                .executionId("exec-xyz-456")
                .correlationKey("pratica-556101")
                .type(TaskEventType.PROGRESS)
                .timestamp(FIXED_TIMESTAMP)
                .sequenceNumber(7)
                .message("Half of the file processed")
                .progress(50)
                .build();

        String line = serializer.serialize(original);
        TaskEvent restored = serializer.deserialize(line);

        assertThat(line).doesNotContain("\n").doesNotContain("\r");
        assertThat(restored.eventId()).isEqualTo(original.eventId());
        assertThat(restored.taskName()).isEqualTo(original.taskName());
        assertThat(restored.executionId()).isEqualTo(original.executionId());
        assertThat(restored.correlationKey()).isEqualTo(original.correlationKey());
        assertThat(restored.type()).isEqualTo(original.type());
        assertThat(restored.timestamp()).isEqualTo(original.timestamp());
        assertThat(restored.sequenceNumber()).isEqualTo(original.sequenceNumber());
        assertThat(restored.message()).isEqualTo(original.message());
        assertThat(restored.progress()).isEqualTo(original.progress());
    }

    @Test
    void roundTrip_nullOptionalFields() {
        TaskEvent original = TaskEvent.builder()
                .eventId("event-1")
                .taskName("BATCH_JOB")
                .executionId("exec-1")
                .correlationKey(null)
                .type(TaskEventType.STARTED)
                .timestamp(FIXED_TIMESTAMP)
                .sequenceNumber(0)
                .message(null)
                .progress(null)
                .build();

        String line = serializer.serialize(original);
        TaskEvent restored = serializer.deserialize(line);

        assertThat(restored.correlationKey()).isNull();
        assertThat(restored.message()).isNull();
        assertThat(restored.progress()).isNull();
        assertThat(restored.type()).isEqualTo(TaskEventType.STARTED);
        assertThat(restored.sequenceNumber()).isEqualTo(0);
    }

    @Test
    void roundTrip_messageContainingSpacesNewlinesAndUnicode() {
        // Base64 encoding must survive all of these safely
        String trickMessage = "Line one\nLine two\r\nAnd Unicode: café, 日本語, emoji 🚀|pipe|";

        TaskEvent original = TaskEvent.builder()
                .eventId("event-tricky")
                .taskName("UNICODE_TASK")
                .executionId("exec-tricky")
                .type(TaskEventType.INFO)
                .timestamp(FIXED_TIMESTAMP)
                .sequenceNumber(3)
                .message(trickMessage)
                .build();

        String line = serializer.serialize(original);
        TaskEvent restored = serializer.deserialize(line);

        assertThat(line).doesNotContain("\n").doesNotContain("\r");
        assertThat(restored.message()).isEqualTo(trickMessage);
    }

    @Test
    void roundTrip_allEventTypes() {
        for (TaskEventType type : TaskEventType.values()) {
            TaskEvent original = minimalBuilder().type(type).build();
            TaskEvent restored = serializer.deserialize(serializer.serialize(original));
            assertThat(restored.type()).isEqualTo(type);
        }
    }

    @Test
    void roundTrip_progressBoundaryValues() {
        TaskEvent zeroProgress = minimalBuilder().progress(0).build();
        TaskEvent fullProgress = minimalBuilder().progress(100).build();

        assertThat(serializer.deserialize(serializer.serialize(zeroProgress)).progress()).isEqualTo(0);
        assertThat(serializer.deserialize(serializer.serialize(fullProgress)).progress()).isEqualTo(100);
    }

    @Test
    void deserialize_tooFewFields_throwsIllegalArgumentException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serializer.deserialize("only|three|fields"))
                .withMessageContaining("expected 9 fields");
    }

    @Test
    void deserialize_tooManyFields_throwsIllegalArgumentException() {
        String tooMany = "a|b|c|d|e|f|g|h|i|j";
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serializer.deserialize(tooMany))
                .withMessageContaining("expected 9 fields");
    }

    @Test
    void deserialize_unknownEventType_throwsIllegalArgumentException() {
        // Build a valid line, then corrupt the type field
        String validLine = serializer.serialize(minimalBuilder().build());
        String[] parts = validLine.split("\\|", -1);
        parts[4] = "BOGUS_TYPE";
        String corruptedLine = String.join("|", parts);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serializer.deserialize(corruptedLine))
                .withMessageContaining("BOGUS_TYPE");
    }

    @Test
    void deserialize_invalidTimestamp_throwsIllegalArgumentException() {
        String validLine = serializer.serialize(minimalBuilder().build());
        String[] parts = validLine.split("\\|", -1);
        parts[5] = "not-a-number";
        String corruptedLine = String.join("|", parts);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serializer.deserialize(corruptedLine))
                .withMessageContaining("timestamp");
    }

    @Test
    void deserialize_invalidBase64InRequiredField_throwsIllegalArgumentException() {
        String validLine = serializer.serialize(minimalBuilder().build());
        String[] parts = validLine.split("\\|", -1);
        // Corrupt eventId field with invalid Base64
        parts[0] = "!!!not-base64!!!";
        String corruptedLine = String.join("|", parts);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serializer.deserialize(corruptedLine))
                .withMessageContaining("eventId");
    }

    @Test
    void deserialize_emptyLine_throwsIllegalArgumentException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> serializer.deserialize(""))
                .withMessageContaining("expected 9 fields");
    }

    // --- helpers ---

    private static TaskEvent.Builder minimalBuilder() {
        return TaskEvent.builder()
                .eventId("event-1")
                .taskName("TASK_NAME")
                .executionId("exec-1")
                .type(TaskEventType.STARTED)
                .timestamp(FIXED_TIMESTAMP)
                .sequenceNumber(0);
    }
}
