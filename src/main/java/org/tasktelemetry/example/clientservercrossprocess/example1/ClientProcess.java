package org.tasktelemetry.example.clientservercrossprocess.example1;

import java.time.Duration;

import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.transport.crossprocess.SocketTaskTransport;
import org.tasktelemetry.watch.TaskWatcher;

/**
 * Client process (consumer): connects to the hub and observes the upload running
 * in another process. It waits up to a timeout for the upload to be in progress,
 * shows its progress, and waits for it to finish; if the task's heart stops, the
 * watcher returns {@code LOST}.
 */
public final class ClientProcess {

    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(30);

    private ClientProcess() {
    }

    public static void main(String[] args) {
        try (SocketTaskTransport transport = new SocketTaskTransport(ExampleConfig.HOST, ExampleConfig.PORT);
             TaskWatcher watcher = new TaskWatcher(transport, ExampleConfig.TASK_NAME)) {

            watcher.onProgress(percent -> System.out.println("  upload at " + percent + "%"));

            if (!watcher.awaitStart(DETECT_TIMEOUT)) {
                System.out.println("No upload in progress within the timeout.");
                return;
            }

            System.out.println("Upload in progress: waiting for it to finish...");
            TaskExecutionStatus status = watcher.awaitCompletion();
            System.out.println("Upload finished with status: " + status);
        }
    }
}
