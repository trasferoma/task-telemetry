package org.tasktelemetry.transport.crossprocess;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;

/**
 * Default {@link TaskEventSerializer} that encodes each field with Base64
 * URL-safe encoding and joins them with {@code |} as a delimiter.
 *
 * <h2>Wire format</h2>
 * A single line of 9 pipe-delimited fields:
 * <pre>
 *   eventId | taskName | executionId | correlationKey | type | timestampMillis | sequenceNumber | message | progress
 * </pre>
 *
 * <h2>Encoding rules</h2>
 * <ul>
 *   <li>String fields (eventId, taskName, executionId, correlationKey, message)
 *       are encoded with Base64 URL-safe, no padding. This makes them safe to
 *       embed between {@code |} delimiters and guarantees no embedded newlines.</li>
 *   <li>Null optional fields (correlationKey, message, progress) are represented
 *       by the sentinel {@code -}. An empty string is represented as the empty
 *       Base64 token {@code ""}, so null and empty-string are unambiguous.</li>
 *   <li>{@code type} is the enum name (all-ASCII, no encoding needed).</li>
 *   <li>{@code timestamp} is epoch-milliseconds (long).</li>
 *   <li>{@code sequenceNumber} is a plain decimal long.</li>
 *   <li>{@code progress} is a plain decimal integer or the {@code -} sentinel.</li>
 *   <li>{@code payload} is never transmitted; it will always be {@code null} after
 *       deserialization.</li>
 * </ul>
 */
public final class TextTaskEventSerializer implements TaskEventSerializer {

    /** Sentinel token that represents a null optional field on the wire. */
    private static final String NULL_SENTINEL = "-";

    private static final String DELIMITER = "|";
    private static final String DELIMITER_REGEX = "\\|";

    /** Expected number of pipe-delimited fields in a valid wire line. */
    private static final int FIELD_COUNT = 9;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    @Override
    public String serialize(TaskEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        return String.join(DELIMITER,
                encodeText(event.eventId()),
                encodeText(event.taskName()),
                encodeText(event.executionId()),
                encodeNullableText(event.correlationKey()),
                event.type().name(),
                String.valueOf(event.timestamp().toEpochMilli()),
                String.valueOf(event.sequenceNumber()),
                encodeNullableText(event.message()),
                encodeNullableInt(event.progress()));
    }

    @Override
    public TaskEvent deserialize(String line) {
        Objects.requireNonNull(line, "line must not be null");

        String[] fields = line.split(DELIMITER_REGEX, -1);
        if (fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "Malformed event line: expected " + FIELD_COUNT + " fields but found "
                            + fields.length + " in: [" + line + "]");
        }

        String eventId = decodeText(fields[0], "eventId");
        String taskName = decodeText(fields[1], "taskName");
        String executionId = decodeText(fields[2], "executionId");
        String correlationKey = decodeNullableText(fields[3], "correlationKey");
        TaskEventType type = parseType(fields[4]);
        Instant timestamp = parseTimestamp(fields[5]);
        long sequenceNumber = parseSequenceNumber(fields[6]);
        String message = decodeNullableText(fields[7], "message");
        Integer progress = parseNullableInt(fields[8]);

        return TaskEvent.builder()
                .eventId(eventId)
                .taskName(taskName)
                .executionId(executionId)
                .correlationKey(correlationKey)
                .type(type)
                .timestamp(timestamp)
                .sequenceNumber(sequenceNumber)
                .message(message)
                .progress(progress)
                .build();
    }

    // --- encode helpers ---

    private static String encodeText(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeNullableText(String value) {
        if (value == null) {
            return NULL_SENTINEL;
        }
        return encodeText(value);
    }

    private static String encodeNullableInt(Integer value) {
        if (value == null) {
            return NULL_SENTINEL;
        }
        return Integer.toString(value);
    }

    // --- decode helpers ---

    private static String decodeText(String token, String fieldName) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException(
                    "Malformed event line: field '" + fieldName + "' must not be empty");
        }
        try {
            return new String(DECODER.decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Malformed event line: field '" + fieldName + "' is not valid Base64: [" + token + "]", ex);
        }
    }

    private static String decodeNullableText(String token, String fieldName) {
        if (NULL_SENTINEL.equals(token)) {
            return null;
        }
        if (token.isEmpty()) {
            // empty Base64 token represents an empty string (not null)
            return "";
        }
        try {
            return new String(DECODER.decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Malformed event line: field '" + fieldName + "' is not valid Base64: [" + token + "]", ex);
        }
    }

    private static TaskEventType parseType(String token) {
        try {
            return TaskEventType.valueOf(token);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Malformed event line: unknown event type: [" + token + "]", ex);
        }
    }

    private static Instant parseTimestamp(String token) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(token));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Malformed event line: timestamp is not a valid long: [" + token + "]", ex);
        }
    }

    private static long parseSequenceNumber(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Malformed event line: sequenceNumber is not a valid long: [" + token + "]", ex);
        }
    }

    private static Integer parseNullableInt(String token) {
        if (NULL_SENTINEL.equals(token)) {
            return null;
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Malformed event line: progress is not a valid integer: [" + token + "]", ex);
        }
    }
}
