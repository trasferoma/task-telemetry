package org.tasktelemetry.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JulTaskTelemetryLoggerTest {

    private final List<LogRecord> records = new ArrayList<>();
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("org.tasktelemetry.test." + getClass().getName());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new CapturingHandler(records));
    }

    @Test
    void prependsPrefixToMessage() {
        TaskTelemetryLogger telemetryLogger = new JulTaskTelemetryLogger(logger, "task-telemetry -");

        telemetryLogger.warning("import failed");

        assertThat(records).singleElement()
                .satisfies(record -> {
                    assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                    assertThat(record.getMessage()).isEqualTo("task-telemetry - import failed");
                });
    }

    @Test
    void attachesThrowableToWarning() {
        TaskTelemetryLogger telemetryLogger = new JulTaskTelemetryLogger(logger, "task-telemetry -");
        RuntimeException failure = new RuntimeException("boom");

        telemetryLogger.warning("publish failed", failure);

        assertThat(records).singleElement()
                .satisfies(record -> assertThat(record.getThrown()).isSameAs(failure));
    }

    @Test
    void attachesThrowableToError() {
        TaskTelemetryLogger telemetryLogger = new JulTaskTelemetryLogger(logger, "task-telemetry -");
        RuntimeException failure = new RuntimeException("boom");

        telemetryLogger.error("publish failed", failure);

        assertThat(records).singleElement()
                .satisfies(record -> {
                    assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
                    assertThat(record.getMessage()).isEqualTo("task-telemetry - publish failed");
                    assertThat(record.getThrown()).isSameAs(failure);
                });
    }

    @Test
    void leavesMessageUntouchedWhenPrefixIsEmpty() {
        TaskTelemetryLogger telemetryLogger = new JulTaskTelemetryLogger(logger, "");

        telemetryLogger.error("plain message");

        assertThat(records).singleElement()
                .satisfies(record -> assertThat(record.getMessage()).isEqualTo("plain message"));
    }

    private static final class CapturingHandler extends Handler {

        private final List<LogRecord> records;

        private CapturingHandler(List<LogRecord> records) {
            this.records = records;
        }

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
    }
}
