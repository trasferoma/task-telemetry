/**
 * Localhost cross-process transport for task-telemetry.
 *
 * <p>This package provides a TCP-based relay that lets multiple JVM processes
 * exchange {@link org.tasktelemetry.event.TaskEvent}s through a shared hub
 * listening on localhost.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   Process A                      Process B
 *   SocketTaskTransport            SocketTaskTransport
 *       |  publish(event)               ^ onEvent(event)
 *       |                               |
 *       +---------> CrossProcessTaskHub-+
 *                   (localhost TCP relay)
 * </pre>
 *
 * <h2>Constraints</h2>
 * <ul>
 *   <li><b>Best-effort, live, no replay:</b> events published while a consumer
 *       is not connected are not delivered to it. No persistence, no queue.</li>
 *   <li><b>payload not transmitted:</b> the {@code payload} field of
 *       {@link org.tasktelemetry.event.TaskEvent} is dropped during serialization
 *       and will be {@code null} on the receiving side (v1 limitation).</li>
 *   <li><b>No auto-reconnect:</b> if the hub closes or the connection is lost,
 *       the {@link org.tasktelemetry.transport.crossprocess.SocketTaskTransport}
 *       does not attempt to reconnect automatically in v1.</li>
 *   <li><b>Self-contained:</b> deleting this package removes the feature without
 *       touching any class in the core.</li>
 * </ul>
 */
package org.tasktelemetry.transport.crossprocess;
