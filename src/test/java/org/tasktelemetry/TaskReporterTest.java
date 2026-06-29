package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.event.TaskExecutionDescriptor;
import org.tasktelemetry.event.TaskFailure;
import org.tasktelemetry.transport.TaskTransport;

@ExtendWith(MockitoExtension.class)
class TaskReporterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-28T10:00:00Z");
    private static final TaskExecutionDescriptor DESCRIPTOR =
            new TaskExecutionDescriptor("IMPORT_CLIENTI", "exec-1", "pratica-556101");

    @Mock
    private TaskTransport transport;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private TaskReporter reporter;

    @AfterEach
    void closeReporter() {
        if (reporter != null) {
            reporter.close();
        }
    }

    @Test
    void constructorEmitsStartedAsFirstEvent() {
        reporter = newReporter();

        TaskEvent started = publishedEvents().get(0);
        assertThat(started.type()).isEqualTo(TaskEventType.STARTED);
        assertThat(started.sequenceNumber()).isZero();
        assertThat(started.eventId()).isEqualTo("exec-1-0");
        assertThat(started.taskName()).isEqualTo("IMPORT_CLIENTI");
        assertThat(started.executionId()).isEqualTo("exec-1");
        assertThat(started.correlationKey()).isEqualTo("pratica-556101");
        assertThat(started.timestamp()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void progressEmitsProgressEventWithPercentage() {
        reporter = newReporter();

        reporter.progress(50, "halfway");

        TaskEvent progress = publishedEvents().get(1);
        assertThat(progress.type()).isEqualTo(TaskEventType.PROGRESS);
        assertThat(progress.progress()).isEqualTo(50);
        assertThat(progress.message()).isEqualTo("halfway");
        assertThat(progress.sequenceNumber()).isEqualTo(1);
        assertThat(progress.eventId()).isEqualTo("exec-1-1");
    }

    @Test
    void infoWarningHeartbeatAndCustomEmitMatchingTypes() {
        reporter = newReporter();

        reporter.info("info message");
        reporter.warning("warning message");
        reporter.heartbeat();
        reporter.custom("custom message", "payload");

        List<TaskEvent> events = publishedEvents();
        assertThat(events).extracting(TaskEvent::type).containsExactly(
                TaskEventType.STARTED,
                TaskEventType.INFO,
                TaskEventType.WARNING,
                TaskEventType.HEARTBEAT,
                TaskEventType.CUSTOM);
        assertThat(events.get(4).payload()).isEqualTo("payload");
    }

    @Test
    void sequenceNumbersAreMonotonic() {
        reporter = newReporter();

        reporter.progress(10, "a");
        reporter.progress(20, "b");

        assertThat(publishedEvents())
                .extracting(TaskEvent::sequenceNumber)
                .containsExactly(0L, 1L, 2L);
    }

    @Test
    void completedEmitsTerminalSuccess() {
        reporter = newReporter();

        reporter.completed("done");

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.COMPLETED);
    }

    @Test
    void failedEmitsFailureWithExceptionTypeMessageAndStackTrace() {
        reporter = newReporter();

        reporter.failed(new IllegalStateException("boom"));

        TaskEvent failed = publishedEvents().get(1);
        assertThat(failed.type()).isEqualTo(TaskEventType.FAILED);
        assertThat(failed.message()).contains("boom");
        assertThat(failed.payload()).isInstanceOf(TaskFailure.class);

        TaskFailure failure = (TaskFailure) failed.payload();
        assertThat(failure.exceptionType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failure.message()).isEqualTo("boom");
        assertThat(failure.stackTrace()).contains("IllegalStateException");
    }

    @Test
    void failedOmitsStackTraceWhenDisabled() {
        reporter = new TaskReporter(DESCRIPTOR, transport,
                TaskReporterSettings.defaults().withClock(clock).withIncludeStackTrace(false));

        reporter.failed(new IllegalStateException("boom"));

        TaskFailure failure = (TaskFailure) publishedEvents().get(1).payload();
        assertThat(failure.exceptionType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failure.message()).isEqualTo("boom");
        assertThat(failure.stackTrace()).isNull();
    }

    @Test
    void cancelledEmitsTerminalCancellation() {
        reporter = newReporter();

        reporter.cancelled("stopped by user");

        TaskEvent cancelled = publishedEvents().get(1);
        assertThat(cancelled.type()).isEqualTo(TaskEventType.CANCELLED);
        assertThat(cancelled.message()).isEqualTo("stopped by user");
    }

    @Test
    void descriptorReturnsTheBoundExecutionIdentity() {
        reporter = newReporter();

        assertThat(reporter.descriptor()).isEqualTo(DESCRIPTOR);
    }

    @Test
    void emittingAfterTerminalEventThrows() {
        reporter = newReporter();
        reporter.completed("done");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> reporter.progress(10, "late"));
    }

    @Test
    void closeWithoutTerminalEmitsCancelledByDefault() {
        reporter = newReporter();

        reporter.close();

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.CANCELLED);
    }

    @Test
    void closeWithFailedBehaviorEmitsFailure() {
        reporter = new TaskReporter(DESCRIPTOR, transport,
                TaskReporterSettings.defaults().withClock(clock)
                        .withCloseBehavior(TaskReporter.CloseBehavior.FAILED));

        reporter.close();

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.FAILED);
    }

    @Test
    void closeWithIgnoreBehaviorEmitsNoTerminal() {
        reporter = new TaskReporter(DESCRIPTOR, transport,
                TaskReporterSettings.defaults().withClock(clock)
                        .withCloseBehavior(TaskReporter.CloseBehavior.IGNORE));

        reporter.close();

        verify(transport, times(1)).publish(any());
    }

    @Test
    void closeAfterTerminalEmitsNoAdditionalEvent() {
        reporter = newReporter();
        reporter.completed("done");

        reporter.close();
        reporter.close();

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(TaskEventType.STARTED, TaskEventType.COMPLETED);
    }

    @Test
    void tryWithResourcesWithoutTerminalEmitsCancelled() {
        try (TaskReporter autoClosed = newReporter()) {
            autoClosed.progress(10, "working");
        }

        assertThat(publishedEvents())
                .extracting(TaskEvent::type)
                .containsExactly(
                        TaskEventType.STARTED, TaskEventType.PROGRESS, TaskEventType.CANCELLED);
    }

    @Test
    @SuppressWarnings("resource") // constructors are expected to throw, no reporter is created
    void constructorRejectsNullArguments() {
        TaskReporterSettings settings = TaskReporterSettings.defaults().withClock(clock);
        assertThatNullPointerException().isThrownBy(
                () -> new TaskReporter(null, transport, settings));
        assertThatNullPointerException().isThrownBy(
                () -> new TaskReporter(DESCRIPTOR, null, settings));
        assertThatNullPointerException().isThrownBy(
                () -> new TaskReporter(DESCRIPTOR, transport, null));
    }

    private TaskReporter newReporter() {
        return new TaskReporter(DESCRIPTOR, transport, TaskReporterSettings.defaults().withClock(clock));
    }

    private List<TaskEvent> publishedEvents() {
        ArgumentCaptor<TaskEvent> captor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(transport, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }
}
