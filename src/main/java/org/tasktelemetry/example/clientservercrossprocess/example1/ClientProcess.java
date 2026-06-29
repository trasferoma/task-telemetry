package org.tasktelemetry.example.clientservercrossprocess.example1;

import java.time.Duration;

import org.tasktelemetry.monitor.TaskExecutionStatus;
import org.tasktelemetry.transport.crossprocess.SocketClientTaskTransport;
import org.tasktelemetry.transport.crossprocess.TaskUnreachableException;
import org.tasktelemetry.watch.TaskWatcher;

/**
 * Client process (consumer): connects to the task's server and observes the upload
 * running in another process. It waits up to a timeout for the upload to be in
 * progress, shows its progress, and waits for it to finish; if the task's heart
 * stops, the watcher returns {@code LOST}.
 *
 * <p>The task process ({@link TaskProcess}) must already be running and listening
 * before this client starts. If the connection fails, this process exits with a
 * clear error message.
 */
public final class ClientProcess {

    private static final Duration DETECT_TIMEOUT = Duration.ofSeconds(30);

    private ClientProcess() {
    }

    public static void main(String[] args) {
        SocketClientTaskTransport transport;
        try {
            transport = new SocketClientTaskTransport(ExampleConfig.HOST, ExampleConfig.PORT);
        } catch (TaskUnreachableException ex) {
            System.err.println("Cannot reach task server at "
                    + ExampleConfig.HOST + ":" + ExampleConfig.PORT
                    + " - make sure TaskProcess is running first.");
            System.err.println("Cause: " + ex.getMessage());
            return;
        }

        try (transport;
             TaskWatcher watcher = new TaskWatcher(transport, ExampleConfig.TASK_NAME)) {

            watcher.onProgress(percent -> System.out.println("  upload at " + percent + "%"));
            watcher.onHeartbeat(() -> System.out.println("  heartbeat (task alive)"));

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
