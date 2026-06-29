package org.tasktelemetry.event;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Structured details of a failure, carried as the payload of a
 * {@link TaskEventType#FAILED} event (see SPEC §7.7).
 *
 * <p>It uses only strings so it stays transport-friendly (unlike a raw
 * {@link Throwable}). The {@code stackTrace} is optional and may be {@code null}
 * when stack-trace capture is disabled.
 *
 * @param exceptionType fully qualified class name of the throwable, required
 * @param message       the throwable message, may be {@code null}
 * @param stackTrace    the rendered stack trace, or {@code null} when not captured
 */
public record TaskFailure(String exceptionType, String message, String stackTrace) {

    public TaskFailure {
        Objects.requireNonNull(exceptionType, "exceptionType must not be null");
        if (exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must not be blank");
        }
    }

    /**
     * Builds the failure details from a throwable.
     *
     * @param error             the throwable, required
     * @param includeStackTrace whether to capture the rendered stack trace
     * @return the failure details
     */
    public static TaskFailure from(Throwable error, boolean includeStackTrace) {
        Objects.requireNonNull(error, "error must not be null");

        String stackTrace = includeStackTrace ? renderStackTrace(error) : null;
        return new TaskFailure(error.getClass().getName(), error.getMessage(), stackTrace);
    }

    private static String renderStackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
