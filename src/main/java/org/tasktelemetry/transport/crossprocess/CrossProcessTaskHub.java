package org.tasktelemetry.transport.crossprocess;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Localhost TCP relay that forwards text lines among all connected clients.
 *
 * <p>Each connected client may both send and receive lines. When a line arrives
 * from one connection it is broadcast to all <em>other</em> currently-connected
 * clients. The hub is deliberately line-oriented and serializer-agnostic: it
 * never deserializes the content, it just relays raw text.
 *
 * <h2>Threading model</h2>
 * <ul>
 *   <li>One daemon accept-loop thread ({@code task-telemetry-crossprocess-hub-accept-N}).</li>
 *   <li>One daemon reader thread per accepted connection
 *       ({@code task-telemetry-crossprocess-hub-reader-N}).</li>
 * </ul>
 *
 * <h2>Fault isolation</h2>
 * An I/O failure on one connection drops only that connection; other connections
 * are unaffected. Events published while a client is not connected are not
 * delivered to it (best-effort, live, no replay).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (CrossProcessTaskHub hub = new CrossProcessTaskHub(0)) {
 *     int actualPort = hub.port();
 *     // connect SocketTaskTransport instances to localhost:actualPort
 * }
 * }</pre>
 */
public final class CrossProcessTaskHub implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(CrossProcessTaskHub.class.getName());
    private static final String THREAD_PREFIX = "task-telemetry-crossprocess-hub-";

    private static final AtomicInteger HUB_COUNTER = new AtomicInteger(1);

    private final ServerSocket serverSocket;
    private final CopyOnWriteArrayList<ClientConnection> connections = new CopyOnWriteArrayList<>();
    private final String hubName;

    /**
     * Creates a hub bound to the given port.
     * Use {@code port = 0} to let the OS assign an ephemeral port; read the
     * actual port back via {@link #port()}.
     *
     * @param port the TCP port to bind; 0 for ephemeral
     * @throws IOException if the server socket cannot be created
     */
    public CrossProcessTaskHub(int port) throws IOException {
        this.hubName = String.valueOf(HUB_COUNTER.getAndIncrement());
        this.serverSocket = new ServerSocket(port);
        startAcceptLoop();
    }

    /**
     * Returns the actual TCP port this hub is listening on.
     *
     * @return the bound port
     */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * Stops accepting new connections, closes the server socket, and closes all
     * existing client connections.
     */
    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "task-telemetry - hub " + hubName + ": error closing server socket", ex);
        }

        for (ClientConnection connection : connections) {
            connection.close();
        }
        connections.clear();
    }

    private void startAcceptLoop() {
        Thread acceptThread = new Thread(this::acceptLoop, THREAD_PREFIX + "accept-" + hubName);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                ClientConnection connection = new ClientConnection(socket);
                connections.add(connection);
                connection.startReaderThread();
            } catch (SocketException ex) {
                // Normal shutdown: serverSocket.close() triggers this.
                break;
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "task-telemetry - hub " + hubName + ": accept error", ex);
            }
        }
    }

    private void broadcast(String line, ClientConnection sender) {
        for (ClientConnection connection : connections) {
            if (connection != sender) {
                connection.send(line);
            }
        }
    }

    private void removeConnection(ClientConnection connection) {
        connections.remove(connection);
    }

    // --- Inner class ---

    private final class ClientConnection {

        private static final AtomicInteger READER_COUNTER = new AtomicInteger(1);

        private final Socket socket;
        private final BufferedWriter writer;

        ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void startReaderThread() {
            Thread readerThread = new Thread(this::readLoop,
                    THREAD_PREFIX + "reader-" + READER_COUNTER.getAndIncrement());
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void readLoop() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    broadcast(line, this);
                }
            } catch (SocketException ex) {
                // Normal: socket was closed (either by close() or by the remote side).
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "task-telemetry - hub " + hubName + ": read error", ex);
            } finally {
                removeConnection(this);
                close();
            }
        }

        synchronized void send(String line) {
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (IOException ex) {
                // Connection is broken — drop it so other clients are not affected.
                LOGGER.log(Level.WARNING, "task-telemetry - hub " + hubName + ": write error, dropping connection", ex);
                removeConnection(this);
                close();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "task-telemetry - hub " + hubName + ": error closing connection socket", ex);
            }
        }
    }
}
