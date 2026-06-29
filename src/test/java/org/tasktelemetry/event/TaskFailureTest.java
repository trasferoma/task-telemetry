package org.tasktelemetry.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class TaskFailureTest {

    @Test
    void fromThrowableCapturesTypeMessageAndStackTrace() {
        TaskFailure failure = TaskFailure.from(new IllegalStateException("boom"), true);

        assertThat(failure.exceptionType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failure.message()).isEqualTo("boom");
        assertThat(failure.stackTrace())
                .contains("java.lang.IllegalStateException: boom")
                .contains("at ");
    }

    @Test
    void fromThrowableOmitsStackTraceWhenNotRequested() {
        TaskFailure failure = TaskFailure.from(new IllegalStateException("boom"), false);

        assertThat(failure.stackTrace()).isNull();
    }

    @Test
    void fromThrowableAllowsNullMessage() {
        TaskFailure failure = TaskFailure.from(new IllegalStateException(), true);

        assertThat(failure.exceptionType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failure.message()).isNull();
    }

    @Test
    void rejectsNullThrowable() {
        assertThatNullPointerException().isThrownBy(() -> TaskFailure.from(null, true));
    }

    @Test
    void rejectsBlankExceptionType() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TaskFailure("  ", "boom", null));
    }
}
