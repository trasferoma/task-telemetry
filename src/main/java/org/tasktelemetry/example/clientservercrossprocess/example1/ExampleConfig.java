package org.tasktelemetry.example.clientservercrossprocess.example1;

/**
 * Shared settings and run instructions for the cross-process upload demo.
 *
 * <p>Three separate JVMs talk over the localhost cross-process transport
 * (package {@code org.tasktelemetry.transport.crossprocess}):
 *
 * <ol>
 *   <li>{@link HubProcess} — the relay hub; start it first and leave it running.</li>
 *   <li>{@link ClientProcess} — the consumer; it waits for the upload and shows
 *       progress. Start it before (or while) the task runs.</li>
 *   <li>{@link TaskProcess} — the producer; it runs the simulated upload.</li>
 * </ol>
 *
 * <p>Run each in its own terminal, for example:
 *
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example1.HubProcess
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example1.ClientProcess
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example1.TaskProcess
 * }</pre>
 */
public final class ExampleConfig {

    public static final String HOST = "localhost";
    public static final int PORT = 5555;
    public static final String TASK_NAME = "FILE_UPLOAD";

    private ExampleConfig() {
    }
}
