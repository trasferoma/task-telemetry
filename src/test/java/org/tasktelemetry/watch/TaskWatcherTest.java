package org.tasktelemetry.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.listener.TaskListener;
import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.transport.TaskTransport;

class TaskWatcherTest {

    private static final String TASK_NAME = "FILE_UPLOAD";
    private static final String EXECUTION_ID = "exec-1";
    private static final Duration AMPLE_TIMEOUT = Duration.ofSeconds(1);

    @Test
    void constructorRejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new TaskWatcher(null, TASK_NAME));
        assertThatNullPointerException().isThrownBy(() -> new TaskWatcher(new ReplayTransport(), null));
    }

    @Test
    void onProgressRejectsNullCallback() {
        try (TaskWatcher watcher = new TaskWatcher(new ReplayTransport(), TASK_NAME)) {
            assertThatNullPointerException().isThrownBy(() -> watcher.onProgress(null));
        }
    }

    @Test
    void awaitCompletionBeforeStartThrows() {
        try (TaskWatcher watcher = new TaskWatcher(new ReplayTransport(), TASK_NAME)) {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(watcher::awaitCompletion);
        }
    }

    @Test
    void awaitStartReturnsTrueWhenTaskIsEmitting() {
        ReplayTransport transport = new ReplayTransport(event(0, TaskEventType.STARTED, null));

        try (TaskWatcher watcher = new TaskWatcher(transport, TASK_NAME)) {
            assertThat(watcher.awaitStart(AMPLE_TIMEOUT)).isTrue();
        }
    }

    @Test
    void onProgressReceivesEachPercentage() {
        ReplayTransport transport = new ReplayTransport(
                event(0, TaskEventType.STARTED, null),
                event(1, TaskEventType.PROGRESS, 25),
                event(2, TaskEventType.PROGRESS, 75));
        List<Integer> progressSeen = new ArrayList<>();

        try (TaskWatcher watcher = new TaskWatcher(transport, TASK_NAME)) {
            watcher.onProgress(progressSeen::add);
            watcher.awaitStart(AMPLE_TIMEOUT);
        }

        assertThat(progressSeen).containsExactly(25, 75);
    }

    @Test
    void onHeartbeatIsInvokedForHeartbeatEventsOnly() {
        ReplayTransport transport = new ReplayTransport(
                event(0, TaskEventType.STARTED, null),
                event(1, TaskEventType.HEARTBEAT, null),
                event(2, TaskEventType.PROGRESS, 50),
                event(3, TaskEventType.HEARTBEAT, null));
        AtomicInteger beats = new AtomicInteger();
        List<Integer> progressSeen = new ArrayList<>();

        try (TaskWatcher watcher = new TaskWatcher(transport, TASK_NAME)) {
            watcher.onHeartbeat(beats::incrementAndGet);
            watcher.onProgress(progressSeen::add);
            watcher.awaitStart(AMPLE_TIMEOUT);
        }

        assertThat(beats).hasValue(2);
        assertThat(progressSeen).containsExactly(50);
    }

    @Test
    void onHeartbeatRejectsNullCallback() {
        try (TaskWatcher watcher = new TaskWatcher(new ReplayTransport(), TASK_NAME)) {
            assertThatNullPointerException().isThrownBy(() -> watcher.onHeartbeat(null));
        }
    }

    @Test
    void awaitCompletionReturnsCompleted() {
        ReplayTransport transport = new ReplayTransport(
                event(0, TaskEventType.STARTED, null),
                event(1, TaskEventType.COMPLETED, null));

        try (TaskWatcher watcher = new TaskWatcher(transport, TASK_NAME)) {
            watcher.awaitStart(AMPLE_TIMEOUT);

            assertThat(watcher.awaitCompletion()).isEqualTo(TaskExecutionStatus.COMPLETED);
        }
    }

    @Test
    void awaitCompletionReportsFailure() {
        ReplayTransport transport = new ReplayTransport(
                event(0, TaskEventType.STARTED, null),
                event(1, TaskEventType.FAILED, null));

        try (TaskWatcher watcher = new TaskWatcher(transport, TASK_NAME)) {
            watcher.awaitStart(AMPLE_TIMEOUT);

            assertThat(watcher.awaitCompletion()).isEqualTo(TaskExecutionStatus.FAILED);
        }
    }

    @Test
    void closeUnsubscribesFromTransport() {
        ReplayTransport transport = new ReplayTransport(event(0, TaskEventType.STARTED, null));

        try (TaskWatcher watcher = new TaskWatcher(transport, TASK_NAME)) {
            watcher.awaitStart(AMPLE_TIMEOUT);
            assertThat(transport.subscriberCount()).isEqualTo(1);
        }

        assertThat(transport.subscriberCount()).isZero();
    }

    private static TaskEvent event(long sequenceNumber, TaskEventType type, Integer progress) {
        return TaskEvent.builder()
                .eventId(EXECUTION_ID + "-" + sequenceNumber)
                .taskName(TASK_NAME)
                .executionId(EXECUTION_ID)
                .type(type)
                .timestamp(Instant.parse("2026-06-29T10:00:00Z"))
                .sequenceNumber(sequenceNumber)
                .progress(progress)
                .build();
    }

    /**
     * Test transport that replays a fixed list of events synchronously to each
     * subscriber as soon as it subscribes, so the watcher's blocking methods
     * resolve deterministically on a single thread.
     */
    private static final class ReplayTransport implements TaskTransport {

        private final List<TaskEvent> toReplay;
        private final List<TaskListener> listeners = new ArrayList<>();

        private ReplayTransport(TaskEvent... events) {
            this.toReplay = List.of(events);
        }

        @Override
        public void publish(TaskEvent event) {
            listeners.forEach(listener -> listener.onEvent(event));
        }

        @Override
        public void subscribe(TaskListener listener) {
            listeners.add(listener);
            toReplay.forEach(listener::onEvent);
        }

        @Override
        public void unsubscribe(TaskListener listener) {
            listeners.remove(listener);
        }

        private int subscriberCount() {
            return listeners.size();
        }
    }
}
