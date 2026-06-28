package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.transport.TaskTransport;

@ExtendWith(MockitoExtension.class)
class TaskReporterErrorHandlingTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-28T10:00:00Z");
    private static final TaskExecutionDescriptor DESCRIPTOR =
            new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");

    @Mock
    private TaskTransport transport;

    @Mock
    private TaskTelemetryErrorHandler errorHandler;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private TaskReporter reporter;

    @AfterEach
    void closeReporter() {
        if (reporter != null) {
            reporter.close();
        }
    }

    @Test
    void publishFailureIsRoutedToErrorHandler() {
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(transport).publish(any());

        reporter = new TaskReporter(
                DESCRIPTOR, transport, clock, TaskReporter.CloseBehavior.CANCELLED,
                null, null, errorHandler);

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(errorHandler).onPublishFailure(eventCaptor.capture(), eq(failure));
        assertThat(eventCaptor.getValue().type()).isEqualTo(TaskEventType.STARTED);
    }

    @Test
    void ignorePolicyLetsTaskContinue() {
        doThrow(new RuntimeException("boom")).when(transport).publish(any());

        reporter = new TaskReporter(
                DESCRIPTOR, transport, clock, TaskReporter.CloseBehavior.CANCELLED,
                null, null, TaskTelemetryErrorHandler.ignore());

        assertThatNoException().isThrownBy(() -> reporter.progress(50, "working"));
    }

    @Test
    @SuppressWarnings("resource") // constructor is expected to throw, no reporter is created
    void rethrowPolicyPropagatesPublishFailure() {
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(transport).publish(any());

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new TaskReporter(
                        DESCRIPTOR, transport, clock, TaskReporter.CloseBehavior.CANCELLED,
                        null, null, TaskTelemetryErrorHandler.rethrow()))
                .isSameAs(failure);
    }
}
