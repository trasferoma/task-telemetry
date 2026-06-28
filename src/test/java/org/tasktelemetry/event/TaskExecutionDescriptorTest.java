package org.tasktelemetry.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskExecutionDescriptorTest {

    @Test
    void of_leavesCorrelationKeyNull() {
        TaskExecutionDescriptor descriptor =
                TaskExecutionDescriptor.of("IMPORT_CLIENTI", "exec-1");

        assertThat(descriptor.taskName()).isEqualTo("IMPORT_CLIENTI");
        assertThat(descriptor.executionId()).isEqualTo("exec-1");
        assertThat(descriptor.correlationKey()).isNull();
    }

    @Test
    void constructor_keepsCorrelationKey() {
        TaskExecutionDescriptor descriptor =
                new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");

        assertThat(descriptor.correlationKey()).isEqualTo("pratica-556101");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankTaskName(String invalidTaskName) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TaskExecutionDescriptor(invalidTaskName, "exec-1", null))
                .withMessageContaining("taskName");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsBlankExecutionId(String invalidExecutionId) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TaskExecutionDescriptor("IMPORT_CLIENTI", invalidExecutionId, null))
                .withMessageContaining("executionId");
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        TaskExecutionDescriptor first =
                new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");
        TaskExecutionDescriptor second =
                new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }
}
