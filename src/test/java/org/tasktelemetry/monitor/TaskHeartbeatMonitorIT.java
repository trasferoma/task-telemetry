package org.tasktelemetry.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskTelemetry;

/**
 * End-to-end integration test of {@link TaskHeartbeatMonitor} as a real listener
 * on the public {@link TaskTelemetry} API: the monitor derives the execution
 * status from the live events of a running task.
 */
class TaskHeartbeatMonitorIT {

    @Test
    void reportsRunningWhileActiveThenCompletedAfterFinish() {
        TaskHeartbeatMonitor monitor = new TaskHeartbeatMonitor(Clock.systemUTC());

        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .heartbeatInterval(null)
                .executionIdGenerator(() -> "exec-1")
                .build()) {

            telemetry.listen().onEvent(monitor).start();

            try (TaskReporter reporter = telemetry.start("IMPORT_CLIENTI")) {
                reporter.progress(50, "working");
                assertThat(monitor.statusOf("exec-1")).isEqualTo(TaskExecutionStatus.RUNNING);
                reporter.completed("done");
            }
        }

        assertThat(monitor.statusOf("exec-1")).isEqualTo(TaskExecutionStatus.COMPLETED);
    }
}
