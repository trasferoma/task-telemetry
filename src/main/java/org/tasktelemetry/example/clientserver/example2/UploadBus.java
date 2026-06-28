package org.tasktelemetry.example.clientserver.example2;

import org.tasktelemetry.transport.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Shared in-memory bus for the example2 demo.
 *
 * <p>It stands in for a real cross-process transport: a producer and a client
 * that each create their own {@link org.tasktelemetry.TaskTelemetry} can exchange
 * events only if they share the same transport. In the same JVM this single
 * shared instance plays that role; with a future cross-process transport this
 * would become the connection to the broker/socket.
 */
public final class UploadBus {

    public static final TaskTransport SHARED_TRANSPORT = new InMemoryTaskTransport();

    private UploadBus() {
    }
}
