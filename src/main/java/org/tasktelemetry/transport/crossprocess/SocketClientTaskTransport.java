package org.tasktelemetry.transport.crossprocess;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.listener.TaskListener;
import org.tasktelemetry.transport.TaskTransport;

/**
 * {@link TaskTransport} for the consumer side of the 2-actor cross-process topology.
 *
 * <p>This client connects to a {@link SocketServerTaskTransport} (the task / producer
 * side) over a localhost TCP socket. From the moment the connection is established,
 * any event published by the server is serialized to a text line and pushed to this
 * client, which deserializes it and dispatches it to all locally-registered listeners.
 *
 * <h2>Topology role</h2>
 * In the 2-actor model the <em>task is the server</em> and the <em>client is the
 * consumer</em>. A client can call {@link #publish} (the bytes are written to the
 * socket), but in this topology the server does not read from clients; the method
 * exists only to satisfy the {@link TaskTransport} contract. Typical consumers
 * should use {@link #subscribe} only.
 *
 * <h2>Connection</h2>
 * The socket is opened during construction. If the server is not reachable, an
 * {@link IllegalStateException} is thrown immediately.
 *
 * <h2>Reader thread</h2>
 * A single daemon thread ({@code task-telemetry-crossprocess-client-N}) reads lines
 * sent by the server, deserializes each one and dispatches it to all locally-subscribed
 * listeners. A listener that throws is isolated; it does not stop the reader loop.
 *
 * <h2>Limitations (v1)</h2>
 * <ul>
 *   <li>No auto-reconnect: if the server closes or the connection is lost, this
 *       client does not reconnect automatically.</li>
 *   <li>The {@code payload} field is not transmitted; it will be {@code null}
 *       on the receiving side.</li>
 *   <li>Events published before this client connected are not replayed.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * All public methods are safe for concurrent use.
 */
public final class SocketClientTaskTransport implements TaskTransport, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SocketClientTaskTransport.class.getName());
    private static final String THREAD_PREFIX = "task-telemetry-crossprocess-client-";

    private static final AtomicInteger CLIENT_COUNTER = new AtomicInteger(1);

    private final Socket socket;
    private final BufferedWriter writer;
    private final TaskEventSerializer serializer;
    private final CopyOnWriteArrayList<TaskListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Connects to the task server at the given host and port using the default
     * {@link TextTaskEventSerializer}.
     *
     * @param host the server host name or IP address
     * @param port the server port
     * @throws IllegalStateException if the connection cannot be established
     */
    public SocketClientTaskTransport(String host, int port) {
        this(host, port, new TextTaskEventSerializer());
    }

    /**
     * Connects to the task server at the given host and port using the provided serializer.
     *
     * @param host       the server host name or IP address
     * @param port       the server port
     * @param serializer the serializer to use for decoding incoming events
     * @throws IllegalStateException if the connection cannot be established
     */
    public SocketClientTaskTransport(String host, int port, TaskEventSerializer serializer) {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(serializer, "serializer must not be null");

        this.serializer = serializer;
        this.socket = connect(host, port);
        this.writer = openWriter(this.socket);
        startReaderThread();
    }

    /**
     * Writes a serialized event to the socket.
     *
     * <p><b>Note:</b> in the 2-actor topology the server ({@link SocketServerTaskTransport})
     * does not read from clients. This method writes the bytes to the socket but they will
     * not be processed by the server. It exists only to satisfy the {@link TaskTransport}
     * contract. Typical consumers should not call this method.
     */
    @Override
    public void publish(TaskEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String line = serializer.serialize(event);
        sendLine(line);
    }

    @Override
    public void subscribe(TaskListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(TaskListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.remove(listener);
    }

    /**
     * Stops the reader thread and closes the socket connection to the server.
     */
    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketClientTaskTransport: error closing socket", ex);
        }
    }

    // --- setup helpers ---

    private static Socket connect(String host, int port) {
        try {
            return new Socket(host, port);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "task-telemetry - SocketClientTaskTransport: cannot connect to server at "
                            + host + ":" + port, ex);
        }
    }

    private static BufferedWriter openWriter(Socket socket) {
        try {
            return new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "task-telemetry - SocketClientTaskTransport: cannot open output stream", ex);
        }
    }

    private void startReaderThread() {
        Thread readerThread = new Thread(this::readLoop,
                THREAD_PREFIX + CLIENT_COUNTER.getAndIncrement());
        readerThread.setDaemon(true);
        readerThread.start();
    }

    // --- I/O ---

    private synchronized void sendLine(String line) {
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            // Best-effort: log the failure but do not crash the caller.
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketClientTaskTransport: failed to write to socket", ex);
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                dispatchLine(line);
            }
        } catch (SocketException ex) {
            // Normal: socket was closed by close() or the server shut down.
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketClientTaskTransport: read error in reader thread", ex);
        }
    }

    private void dispatchLine(String line) {
        TaskEvent event;
        try {
            event = serializer.deserialize(line);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketClientTaskTransport: skipping malformed line: ["
                            + line + "]", ex);
            return;
        }

        List<TaskListener> snapshot = List.copyOf(listeners);
        for (TaskListener listener : snapshot) {
            dispatchToListener(listener, event);
        }
    }

    private void dispatchToListener(TaskListener listener, TaskEvent event) {
        try {
            listener.onEvent(event);
        } catch (Exception ex) {
            // Isolate listener failures so one bad listener cannot kill the reader loop.
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketClientTaskTransport: listener threw an exception", ex);
        }
    }
}
