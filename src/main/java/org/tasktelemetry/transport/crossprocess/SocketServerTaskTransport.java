package org.tasktelemetry.transport.crossprocess;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
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
 * {@link TaskTransport} for the producer (task) side of the 2-actor cross-process topology.
 *
 * <p>The server binds a TCP port on construction and listens for incoming client
 * connections from {@link SocketClientTaskTransport} instances. When {@link #publish}
 * is called, the event is serialized to a text line and written to <em>every</em>
 * currently-connected client (best-effort broadcast). Events published when no
 * client is connected are silently discarded — there is no buffering or replay.
 *
 * <h2>Topology role</h2>
 * In the 2-actor model the <em>task is the server</em> and clients are consumers.
 * The server emits blindly: it does not know how many clients are connected, does
 * not wait for acknowledgements, and does not read from client sockets.
 *
 * <h2>Binding</h2>
 * Pass {@code port = 0} to let the OS assign an ephemeral port; read the actual
 * bound port back via {@link #port()}. This is particularly useful in tests.
 *
 * <h2>Accept loop</h2>
 * A daemon thread ({@code task-telemetry-crossprocess-server-accept-N}) accepts new
 * client connections and adds them to the internal broadcast list. A client that
 * disconnects is detected on the next write attempt and silently removed.
 *
 * <h2>Local in-JVM listeners</h2>
 * {@link #subscribe} / {@link #unsubscribe} register in-JVM listeners that receive
 * events exactly like a remote client, without going through the socket.
 *
 * <h2>Limitations (v1)</h2>
 * <ul>
 *   <li>Events published before a client connects are not delivered (no replay).</li>
 *   <li>Clients are consumer-only; the server does not read from them.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * All public methods are safe for concurrent use.
 */
public final class SocketServerTaskTransport implements TaskTransport, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SocketServerTaskTransport.class.getName());
    private static final String ACCEPT_THREAD_PREFIX = "task-telemetry-crossprocess-server-accept-";

    private static final AtomicInteger SERVER_COUNTER = new AtomicInteger(1);

    private final ServerSocket serverSocket;
    private final TaskEventSerializer serializer;
    private final CopyOnWriteArrayList<ClientWriter> clientWriters = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<TaskListener> localListeners = new CopyOnWriteArrayList<>();
    private final String serverName;

    /**
     * Binds to the given port and starts accepting client connections, using the
     * default {@link TextTaskEventSerializer}.
     *
     * @param port the TCP port to bind; {@code 0} for an OS-assigned ephemeral port
     * @throws IOException if the server socket cannot be created
     */
    public SocketServerTaskTransport(int port) throws IOException {
        this(port, new TextTaskEventSerializer());
    }

    /**
     * Binds to the given port and starts accepting client connections, using the
     * provided serializer.
     *
     * @param port       the TCP port to bind; {@code 0} for an OS-assigned ephemeral port
     * @param serializer the serializer to use for encoding events to the wire
     * @throws IOException if the server socket cannot be created
     */
    public SocketServerTaskTransport(int port, TaskEventSerializer serializer) throws IOException {
        Objects.requireNonNull(serializer, "serializer must not be null");

        this.serializer = serializer;
        this.serverName = String.valueOf(SERVER_COUNTER.getAndIncrement());
        this.serverSocket = new ServerSocket(port);
        startAcceptLoop();
    }

    /**
     * Returns the actual TCP port this server is listening on.
     *
     * <p>Useful when the server was created with {@code port = 0} (ephemeral port).
     *
     * @return the bound port
     */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * Serializes the event and writes it to all currently-connected clients.
     *
     * <p>Delivery is best-effort: if a client socket is broken, that client is
     * dropped and the remaining clients are not affected. If no client is connected
     * the event is discarded. Local in-JVM listeners registered via {@link #subscribe}
     * receive the event regardless of connected clients.
     */
    @Override
    public void publish(TaskEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String line = serializer.serialize(event);
        broadcastToClients(line);
        dispatchToLocalListeners(event);
    }

    /**
     * Registers a local in-JVM listener.
     *
     * @param listener the listener to register, never {@code null}
     */
    @Override
    public void subscribe(TaskListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        localListeners.add(listener);
    }

    /**
     * Removes a local in-JVM listener.
     *
     * @param listener the listener to remove, never {@code null}
     */
    @Override
    public void unsubscribe(TaskListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        localListeners.remove(listener);
    }

    /**
     * Stops the accept loop, closes the server socket, and closes all client connections.
     */
    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - server " + serverName + ": error closing server socket", ex);
        }

        for (ClientWriter writer : clientWriters) {
            writer.close();
        }
        clientWriters.clear();
    }

    // --- accept loop ---

    private void startAcceptLoop() {
        Thread acceptThread = new Thread(this::acceptLoop,
                ACCEPT_THREAD_PREFIX + serverName);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientWriter writer = new ClientWriter(clientSocket);
                clientWriters.add(writer);
            } catch (SocketException ex) {
                // Normal shutdown: serverSocket.close() triggers this.
                break;
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING,
                        "task-telemetry - server " + serverName + ": accept error", ex);
            }
        }
    }

    // --- broadcast ---

    private void broadcastToClients(String line) {
        for (ClientWriter writer : clientWriters) {
            writer.send(line);
        }
    }

    void removeClient(ClientWriter writer) {
        clientWriters.remove(writer);
    }

    // --- local listener dispatch ---

    private void dispatchToLocalListeners(TaskEvent event) {
        List<TaskListener> snapshot = List.copyOf(localListeners);
        for (TaskListener listener : snapshot) {
            dispatchToListener(listener, event);
        }
    }

    private void dispatchToListener(TaskListener listener, TaskEvent event) {
        try {
            listener.onEvent(event);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    "task-telemetry - SocketServerTaskTransport: listener threw an exception", ex);
        }
    }

    // --- per-connection writer ---

    /**
     * Wraps a single accepted client socket for write-only access.
     * The server never reads from clients; it only writes serialized event lines.
     */
    final class ClientWriter {

        private final Socket socket;
        private final BufferedWriter writer;

        ClientWriter(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        synchronized void send(String line) {
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (IOException ex) {
                // The client disconnected — drop it so other clients are not affected.
                LOGGER.log(Level.FINE,
                        "task-telemetry - server " + serverName
                                + ": client disconnected, dropping connection", ex);
                removeClient(this);
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ex) {
                LOGGER.log(Level.FINE,
                        "task-telemetry - server " + serverName
                                + ": error closing client socket", ex);
            }
        }
    }
}
