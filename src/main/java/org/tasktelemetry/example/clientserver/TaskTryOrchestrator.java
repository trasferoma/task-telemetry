package org.tasktelemetry.example.clientserver;

import java.time.Duration;

import org.tasktelemetry.TaskTelemetry;
import org.tasktelemetry.listener.ListenerHandle;

/**
 * Example orchestrator: wires the consumer ({@link TaskTryClient}) and the
 * producer ({@link TaskTry}) on a shared {@link TaskTelemetry} runtime, then runs
 * the task so the client receives its live messages.
 *
 * <p>The heartbeat interval is set short (one second) so that the heartbeats
 * emitted while {@link TaskTry} pauses between steps are easy to observe.
 */
public final class TaskTryOrchestrator {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(1);

    private TaskTryOrchestrator() {
    }

    public static void main(String[] args) {
        try (TaskTelemetry telemetry = TaskTelemetry.builder()
                .heartbeatInterval(HEARTBEAT_INTERVAL)
                .build()) {

            TaskTryClient client = new TaskTryClient();
            ListenerHandle handle = client.listenOn(telemetry);

            TaskTry task = new TaskTry(telemetry);
            task.run();

            handle.stop();
        }
    }
}
