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
 * {@link TaskTransport} that connects to a {@link CrossProcessTaskHub} over a
 * localhost TCP socket, enabling cross-process event exchange.
 *
 * <h2>Publish</h2>
 * {@link #publish} serializes the event to a single line and writes it to the
 * hub. The hub then relays the line to every other connected
 * {@code SocketTaskTransport}. Delivery is best-effort: if the connection is
 * broken the failure is logged and the caller is not interrupted.
 *
 * <h2>Subscribe / Receive</h2>
 * A daemon reader thread reads lines from the hub, deserializes each one to a
 * {@link TaskEvent}, and dispatches it to all locally-registered listeners.
 * A listener that throws does not kill the reader loop.
 *
 * <h2>Limitations (v1)</h2>
 * <ul>
 *   <li>No auto-reconnect: if the hub closes or the connection is lost, this
 *       transport does not attempt to reconnect.</li>
 *   <li>The {@code payload} field is not transmitted; it will be {@code null}
 *       on the receiving side.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * All public methods are safe for concurrent use.
 */
public final class SocketTaskTransport implements TaskTransport, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SocketTaskTransport.class.getName());
    private static final String THREAD_PREFIX = "task-telemetry-crossprocess-reader-";

    private static final AtomicInteger READER_COUNTER = new AtomicInteger(1);

    private final Socket socket;
    private final BufferedWriter writer;
    private final TaskEventSerializer serializer;
    private final CopyOnWriteArrayList<TaskListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Connects to the hub at the given host and port using the default
     * {@link TextTaskEventSerializer}.
     *
     * @param host the hub host name or IP address
     * @param port the hub port
     * @throws IllegalStateException if the connection cannot be established
     */
    public SocketTaskTransport(String host, int port) {
        this(host, port, new TextTaskEventSerializer());
    }

    /**
     * Connects to the hub at the given host and port using the provided serializer.
     *
     * @param host       the hub host name or IP address
     * @param port       the hub port
     * @param serializer the serializer to use for encoding and decoding events
     * @throws IllegalStateException if the connection cannot be established
     */
    public SocketTaskTransport(String host, int port, TaskEventSerializer serializer) {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(serializer, "serializer must not be null");

        this.serializer = serializer;
        this.socket = connect(host, port);
        this.writer = openWriter(this.socket);
        startReaderThread();
    }

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
     * Stops the reader thread and closes the socket connection to the hub.
     */
    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "task-telemetry - SocketTaskTransport: error closing socket", ex);
        }
    }

    // --- setup helpers ---

    private static Socket connect(String host, int port) {
        try {
            return new Socket(host, port);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "task-telemetry - SocketTaskTransport: cannot connect to hub at "
                            + host + ":" + port, ex);
        }
    }

    private static BufferedWriter openWriter(Socket socket) {
        try {
            return new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "task-telemetry - SocketTaskTransport: cannot open output stream", ex);
        }
    }

    private void startReaderThread() {
        Thread readerThread = new Thread(this::readLoop,
                THREAD_PREFIX + READER_COUNTER.getAndIncrement());
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
                    "task-telemetry - SocketTaskTransport: failed to publish event", ex);
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
            // Normal: socket was closed by close() or the hub shut down.
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketTaskTransport: read error in reader thread", ex);
        }
    }

    private void dispatchLine(String line) {
        TaskEvent event;
        try {
            event = serializer.deserialize(line);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketTaskTransport: skipping malformed line: [" + line + "]", ex);
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
                    "task-telemetry - SocketTaskTransport: listener threw an exception", ex);
        }
    }
}
