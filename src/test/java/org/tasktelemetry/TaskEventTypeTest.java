package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskEventTypeTest {

    @ParameterizedTest
    @EnumSource(names = {"COMPLETED", "FAILED", "CANCELLED"})
    void terminalTypes_areTerminal(TaskEventType type) {
        assertThat(type.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"STARTED", "PROGRESS", "INFO", "WARNING", "HEARTBEAT", "CUSTOM"})
    void nonTerminalTypes_areNotTerminal(TaskEventType type) {
        assertThat(type.isTerminal()).isFalse();
    }

    @Test
    void declaresAllExpectedTypes() {
        assertThat(TaskEventType.values()).containsExactlyInAnyOrder(
                TaskEventType.STARTED,
                TaskEventType.PROGRESS,
                TaskEventType.INFO,
                TaskEventType.WARNING,
                TaskEventType.HEARTBEAT,
                TaskEventType.COMPLETED,
                TaskEventType.FAILED,
                TaskEventType.CANCELLED,
                TaskEventType.CUSTOM);
    }
}
