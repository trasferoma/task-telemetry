package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;

class TaskTelemetryErrorHandlerTest {

    private final TaskEvent event = sampleEvent();

    @Test
    void ignoreSwallowsFailure() {
        assertThatNoException().isThrownBy(
                () -> TaskTelemetryErrorHandler.ignore().onPublishFailure(event, new RuntimeException("x")));
    }

    @Test
    void loggingSwallowsFailure() {
        assertThatNoException().isThrownBy(
                () -> TaskTelemetryErrorHandler.logging().onPublishFailure(event, new RuntimeException("x")));
    }

    @Test
    void loggingAppliesGivenPrefixToMessage() {
        Logger logger = Logger.getLogger("org.tasktelemetry");
        List<LogRecord> records = new ArrayList<>();
        Handler capturingHandler = capturingHandler(records);
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(capturingHandler);

        try {
            TaskTelemetryErrorHandler.logging("custom-prefix >>")
                    .onPublishFailure(event, new RuntimeException("x"));
        } finally {
            logger.removeHandler(capturingHandler);
            logger.setUseParentHandlers(useParentHandlers);
        }

        assertThat(records).singleElement()
                .satisfies(record -> assertThat(record.getMessage()).startsWith("custom-prefix >> "));
    }

    @Test
    void rethrowRethrowsRuntimeExceptionAsIs() {
        RuntimeException failure = new RuntimeException("boom");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> TaskTelemetryErrorHandler.rethrow().onPublishFailure(event, failure))
                .isSameAs(failure);
    }

    @Test
    void rethrowWrapsNonRuntimeException() {
        IOException checkedFailure = new IOException("disk");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> TaskTelemetryErrorHandler.rethrow().onPublishFailure(event, checkedFailure))
                .withCause(checkedFailure);
    }

    private static Handler capturingHandler(List<LogRecord> records) {
        return new Handler() {

            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static TaskEvent sampleEvent() {
        return TaskEvent.builder()
                .eventId("exec-1-0")
                .taskName("IMPORT_CLIENTI")
                .executionId("exec-1")
                .type(TaskEventType.STARTED)
                .timestamp(Instant.parse("2026-06-28T10:00:00Z"))
                .sequenceNumber(0)
                .build();
    }
}
