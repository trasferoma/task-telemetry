package org.tasktelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.heartbeat.HeartbeatScheduler;

class TaskReporterSettingsTest {

    private static final HeartbeatScheduler SCHEDULER = (task, interval) -> () -> {
    };

    @Test
    void defaultsAreSane() {
        TaskReporterSettings settings = TaskReporterSettings.defaults();

        assertThat(settings.clock()).isNotNull();
        assertThat(settings.closeBehavior()).isEqualTo(TaskReporter.CloseBehavior.CANCELLED);
        assertThat(settings.errorHandler()).isNotNull();
        assertThat(settings.includeStackTrace()).isTrue();
        assertThat(settings.heartbeatScheduler()).isNull();
        assertThat(settings.heartbeatInterval()).isNull();
    }

    @Test
    void withersReturnUpdatedImmutableCopies() {
        TaskReporterSettings defaults = TaskReporterSettings.defaults();
        Clock clock = Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC);

        TaskReporterSettings updated = defaults
                .withClock(clock)
                .withCloseBehavior(TaskReporter.CloseBehavior.FAILED)
                .withHeartbeat(SCHEDULER, Duration.ofSeconds(5))
                .withErrorHandler(TaskTelemetryErrorHandler.ignore())
                .withIncludeStackTrace(false);

        assertThat(updated.clock()).isEqualTo(clock);
        assertThat(updated.closeBehavior()).isEqualTo(TaskReporter.CloseBehavior.FAILED);
        assertThat(updated.heartbeatScheduler()).isSameAs(SCHEDULER);
        assertThat(updated.heartbeatInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(updated.includeStackTrace()).isFalse();

        // the original is unchanged
        assertThat(defaults.closeBehavior()).isEqualTo(TaskReporter.CloseBehavior.CANCELLED);
        assertThat(defaults.includeStackTrace()).isTrue();
        assertThat(defaults.heartbeatScheduler()).isNull();
    }

    @Test
    void rejectsNullRequiredValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> TaskReporterSettings.defaults().withClock(null));
        assertThatNullPointerException()
                .isThrownBy(() -> TaskReporterSettings.defaults().withCloseBehavior(null));
        assertThatNullPointerException()
                .isThrownBy(() -> TaskReporterSettings.defaults().withErrorHandler(null));
    }
}
