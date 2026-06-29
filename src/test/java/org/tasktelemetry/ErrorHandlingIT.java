package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.transport.inmemory.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;

/**
 * End-to-end integration test of the publish-failure policy with the real
 * {@link InMemoryTaskTransport}: a listener that throws must not break the
 * emitting task under the default policy, but must propagate under
 * {@link TaskTelemetryErrorHandler#rethrow()}.
 */
class ErrorHandlingIT {

    @Test
    void defaultPolicyKeepsTaskRunningWhenListenerThrows() {
        TaskTransport transport = new InMemoryTaskTransport();
        transport.subscribe(event -> {
            throw new RuntimeException("listener boom");
        });

        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .transport(transport)
                .heartbeatInterval(null)
                .build()) {

            assertThatNoException().isThrownBy(() -> {
                try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI")) {
                    reporter.progress(50, "working");
                    reporter.completed("done");
                }
            });
        }
    }

    @Test
    void rethrowPolicyPropagatesListenerFailure() {
        InMemoryTaskTransport transport = new InMemoryTaskTransport();
        transport.subscribe(event -> {
            throw new IllegalStateException("listener boom");
        });

        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .transport(transport)
                .heartbeatInterval(null)
                .errorHandler(TaskTelemetryErrorHandler.rethrow())
                .build()) {

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> telemetry.start("IMPORT_CLIENTI"))
                    .withMessageContaining("listener boom");
        }
    }
}
