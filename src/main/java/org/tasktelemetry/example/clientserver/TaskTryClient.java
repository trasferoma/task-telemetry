package org.tasktelemetry.example.clientserver;

import org.tasktelemetry.TaskTelemetry;
import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.listener.ListenerHandle;

/**
 * Example client (consumer): subscribes a listener that receives the live
 * messages emitted by {@link TaskTry}. It does not run the task itself; the
 * {@link TaskTryOrchestrator} wires it together with the task.
 */
public final class TaskTryClient {

    /**
     * Subscribes this client to {@link TaskTry} messages on the given runtime.
     *
     * @param telemetry the telemetry runtime to listen on
     * @return a handle to stop listening
     */
    public ListenerHandle listenOn(TaskTelemetry telemetry) {
        return telemetry.listen()
                .taskName(TaskTry.TASK_NAME)
                .onEvent(TaskTryClient::onMessage)
                .start();
    }

    private static void onMessage(TaskEvent event) {
        StringBuilder line = new StringBuilder();
        line.append("[").append(event.type()).append("] #").append(event.sequenceNumber());

        if (event.progress() != null) {
            line.append(" ").append(event.progress()).append("%");
        }
        if (event.message() != null) {
            line.append(" - ").append(event.message());
        }

        System.out.println(line);
    }
}
