package org.tasktelemetry.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable description of a single event produced during a task execution.
 *
 * <p>Required fields: {@code eventId}, {@code taskName}, {@code executionId},
 * {@code type}, {@code timestamp} and {@code sequenceNumber}. The remaining
 * fields ({@code correlationKey}, {@code message}, {@code progress} and
 * {@code payload}) are optional and may be {@code null}.
 *
 * <p>The library generates {@code eventId}, {@code timestamp} and
 * {@code sequenceNumber}; this type only carries the values and validates them.
 * Instances are normally built through {@link #builder()}.
 *
 * @param eventId        unique identifier of this single event
 * @param taskName       type of task this event belongs to
 * @param executionId    identifier of the single execution
 * @param correlationKey optional link to an application domain, may be {@code null}
 * @param type           event type
 * @param timestamp      instant the event was generated
 * @param sequenceNumber monotonic position of the event within its execution
 * @param message        optional human-readable message, may be {@code null}
 * @param progress       optional completion percentage ({@code 0}-{@code 100}),
 *                       may be {@code null}
 * @param payload        optional application payload, may be {@code null}
 */
public record TaskEvent(
        String eventId,
        String taskName,
        String executionId,
        String correlationKey,
        TaskEventType type,
        Instant timestamp,
        long sequenceNumber,
        String message,
        Integer progress,
        Object payload) {

    private static final int MIN_PROGRESS = 0;
    private static final int MAX_PROGRESS = 100;

    public TaskEvent {
        requireText(eventId, "eventId");
        requireText(taskName, "taskName");
        requireText(executionId, "executionId");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        requireNonNegativeSequenceNumber(sequenceNumber);
        requireProgressInRange(progress);
    }

    /**
     * Creates an empty builder for {@link TaskEvent}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }

    private static void requireNonNegativeSequenceNumber(long sequenceNumber) {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException(
                    "sequenceNumber must not be negative: " + sequenceNumber);
        }
    }

    private static void requireProgressInRange(Integer progress) {
        if (progress == null) {
            return;
        }

        boolean outOfRange = progress < MIN_PROGRESS || progress > MAX_PROGRESS;
        if (outOfRange) {
            throw new IllegalArgumentException(
                    "progress must be between " + MIN_PROGRESS + " and " + MAX_PROGRESS
                            + ": " + progress);
        }
    }

    /**
     * Fluent builder for {@link TaskEvent}. Optional fields can be left unset.
     */
    public static final class Builder {

        private String eventId;
        private String taskName;
        private String executionId;
        private String correlationKey;
        private TaskEventType type;
        private Instant timestamp;
        private long sequenceNumber;
        private String message;
        private Integer progress;
        private Object payload;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder taskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder correlationKey(String correlationKey) {
            this.correlationKey = correlationKey;
            return this;
        }

        public Builder type(TaskEventType type) {
            this.type = type;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder progress(Integer progress) {
            this.progress = progress;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public TaskEvent build() {
            return new TaskEvent(
                    eventId,
                    taskName,
                    executionId,
                    correlationKey,
                    type,
                    timestamp,
                    sequenceNumber,
                    message,
                    progress,
                    payload);
        }
    }
}
