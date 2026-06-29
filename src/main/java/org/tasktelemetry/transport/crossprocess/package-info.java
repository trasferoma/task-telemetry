/**
 * Localhost cross-process transport for task-telemetry.
 *
 * <p>This package provides a 2-actor TCP-based transport that lets a task JVM
 * emit {@link org.tasktelemetry.event.TaskEvent}s to one or more consumer JVMs
 * without any central relay. The task is the server; clients are consumers.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   Task process (server)               Client process (consumer)
 *   SocketServerTaskTransport           SocketClientTaskTransport
 *       | publish(event)                    ^ onEvent(event)
 *       |                                   |
 *       +---- TCP line ----------------------+
 *             (localhost)
 * </pre>
 *
 * <h2>How it works</h2>
 * <ul>
 *   <li>The <b>task</b> creates a {@link org.tasktelemetry.transport.crossprocess.SocketServerTaskTransport},
 *       which binds a server socket and accepts client connections in a daemon thread.
 *       When the task calls {@code publish(event)}, the event is serialized to a single
 *       text line and written to every currently-connected client (best-effort broadcast).
 *       The task emits blindly — it does not know whether any client is connected.</li>
 *   <li>The <b>client</b> creates a {@link org.tasktelemetry.transport.crossprocess.SocketClientTaskTransport},
 *       which connects to the task's server socket. From the moment of connection, it
 *       receives every line the server sends, deserializes it to a
 *       {@link org.tasktelemetry.event.TaskEvent} and dispatches it to locally-registered
 *       listeners.</li>
 * </ul>
 *
 * <h2>Run order</h2>
 * Start the <b>task process first</b> (it is the server and must be listening before
 * the client tries to connect). Then start the client process(es).
 *
 * <h2>Constraints</h2>
 * <ul>
 *   <li><b>Best-effort, live, no replay:</b> events published before a client connects
 *       are not delivered to it. No persistence, no queue, no retry.</li>
 *   <li><b>payload not transmitted:</b> the {@code payload} field of
 *       {@link org.tasktelemetry.event.TaskEvent} is dropped during serialization
 *       and will be {@code null} on the receiving side (v1 limitation).</li>
 *   <li><b>No auto-reconnect:</b> if the server closes or the connection is lost,
 *       the {@link org.tasktelemetry.transport.crossprocess.SocketClientTaskTransport}
 *       does not attempt to reconnect automatically in v1.</li>
 *   <li><b>Clients are consumer-only:</b> the server does not read from connected clients.</li>
 *   <li><b>Self-contained:</b> deleting this package removes the feature without
 *       touching any class in the core.</li>
 * </ul>
 */
package org.tasktelemetry.transport.crossprocess;
