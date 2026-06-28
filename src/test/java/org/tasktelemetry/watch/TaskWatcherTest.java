package org.tasktelemetry.watch;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.transport.InMemoryTaskTransport;

class TaskWatcherTest {

    @Test
    void awaitCompletionBeforeStartThrows() {
        try (TaskWatcher watcher = new TaskWatcher(new InMemoryTaskTransport(), "FILE_UPLOAD")) {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(watcher::awaitCompletion);
        }
    }
}
