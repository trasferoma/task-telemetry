package org.tasktelemetry.example.clientservercrossprocess.example1;

/**
 * Shared settings and run instructions for the cross-process upload demo.
 *
 * <p>Two separate JVMs communicate over the localhost cross-process transport
 * (package {@code org.tasktelemetry.transport.crossprocess}) using a direct
 * 2-actor topology: the task is the server, the client is the consumer.
 *
 * <ol>
 *   <li>{@link TaskProcess} — the producer and server; start it <em>first</em>
 *       because it binds the port. It runs the simulated upload and emits events
 *       regardless of whether any client is connected.</li>
 *   <li>{@link ClientProcess} — the consumer; it connects to the task's server,
 *       waits for the upload to be in progress, shows progress, and waits for
 *       it to finish. If the task is not running when the client starts,
 *       the connection will fail immediately.</li>
 * </ol>
 *
 * <p>Run each in its own terminal, task first:
 *
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example1.TaskProcess
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example1.ClientProcess
 * }</pre>
 */
public final class ExampleConfig {

    public static final String HOST = "localhost";
    public static final int PORT = 5555;
    public static final String TASK_NAME = "FILE_UPLOAD";

    private ExampleConfig() {
    }
}
