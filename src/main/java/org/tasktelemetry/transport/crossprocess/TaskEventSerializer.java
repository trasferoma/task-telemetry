package org.tasktelemetry.transport.crossprocess;

import org.tasktelemetry.event.TaskEvent;

/**
 * SPI for encoding a {@link TaskEvent} to and from a single text line.
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>One event per line — the serialized form must not contain embedded newlines.</li>
 *   <li>Round-trip fidelity for all transmitted fields. The {@code payload} field
 *       is not part of the wire format (v1 constraint) and will always be
 *       {@code null} after deserialization.</li>
 * </ul>
 */
public interface TaskEventSerializer {

    /**
     * Encodes the given event to a single text line.
     *
     * @param event the event to encode, never {@code null}
     * @return a single line with no embedded newlines, never {@code null}
     */
    String serialize(TaskEvent event);

    /**
     * Decodes a single text line back to a {@link TaskEvent}.
     *
     * <p>The returned event will have a {@code null} payload regardless of
     * what was in the original event before serialization.
     *
     * @param line the line to decode, never {@code null}
     * @return the reconstructed event, never {@code null}
     * @throws IllegalArgumentException if {@code line} is malformed
     */
    TaskEvent deserialize(String line);
}
