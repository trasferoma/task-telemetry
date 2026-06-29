package org.tasktelemetry.example.clientserver.example2;

import java.time.Duration;

import org.tasktelemetry.TaskTelemetry;
import org.tasktelemetry.transport.inmemory.InMemoryTaskTransport;
import org.tasktelemetry.transport.TaskTransport;

/**
 * Shared in-memory infrastructure for the example2 demo.
 *
 * <p>It stands in for a real cross-process transport: a producer and a client
 * communicate only if they share the same transport. Producers use
 * {@link #RUNTIME} to emit; consumers tap {@link #SHARED_TRANSPORT} to listen.
 * In the same JVM these shared instances play that role; with a future
 * cross-process transport this would become the connection to the broker/socket.
 */
public final class UploadBus {

    public static final TaskTransport SHARED_TRANSPORT = new InMemoryTaskTransport();

    public static final TaskTelemetry RUNTIME = TaskTelemetry.builder()
            .transport(SHARED_TRANSPORT)
            .heartbeatInterval(Duration.ofSeconds(2))
            .build();

    private UploadBus() {
    }
}
