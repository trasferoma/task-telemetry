package org.tasktelemetry.example.clientservercrossprocess.example2;

/**
 * Shared settings and run instructions for the cross-process "snapshot" demo.
 *
 * <p>Two separate JVMs over the localhost cross-process transport, 2-actor
 * topology (task = server, client = consumer). Unlike example1, here the client
 * does <em>not</em> wait for the task to finish: it observes for a short window,
 * prints a snapshot of the task's current state, and exits while the task keeps
 * running.
 *
 * <ol>
 *   <li>{@link TaskProcess} — the producer and server; start it <em>first</em>.
 *       It runs a long simulated upload and emits events regardless of listeners.</li>
 *   <li>{@link SnapshotClientProcess} — connects while the task is running, takes
 *       a photograph of the current state, prints it, and terminates immediately
 *       (it does not await completion).</li>
 * </ol>
 *
 * <p>Run each in its own terminal, task first:
 *
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example2.TaskProcess
 * mvn -q exec:java -Dexec.mainClass=org.tasktelemetry.example.clientservercrossprocess.example2.SnapshotClientProcess
 * }</pre>
 */
public final class ExampleConfig {

    public static final String HOST = "localhost";
    public static final int PORT = 5556;
    public static final String TASK_NAME = "FILE_UPLOAD";

    private ExampleConfig() {
    }
}
