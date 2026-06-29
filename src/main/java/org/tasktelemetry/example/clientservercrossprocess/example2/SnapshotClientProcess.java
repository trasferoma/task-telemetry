package org.tasktelemetry.example.clientservercrossprocess.example2;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.listener.ListenerHandle;
import org.tasktelemetry.listener.ListenerRegistration;
import org.tasktelemetry.transport.crossprocess.SocketClientTaskTransport;
import org.tasktelemetry.transport.crossprocess.TaskUnreachableException;

/**
 * Snapshot client: connects to a running task, observes it for a short window,
 * prints a photograph of the current state, and exits — without waiting for the
 * task to finish.
 *
 * <p>The cross-process transport is best-effort and does not replay the past, so
 * the snapshot reflects only what the task emits during the observation window
 * (its current progress, plus heartbeats proving it is alive).
 */
public final class SnapshotClientProcess {

    private static final Duration SNAPSHOT_WINDOW = Duration.ofSeconds(4);

    private SnapshotClientProcess() {
    }

    public static void main(String[] args) {
        SocketClientTaskTransport transport;
        try {
            transport = new SocketClientTaskTransport(ExampleConfig.HOST, ExampleConfig.PORT);
        } catch (TaskUnreachableException ex) {
            System.out.println("Task non raggiungibile su " + ExampleConfig.HOST + ":" + ExampleConfig.PORT
                    + " - avvia prima TaskProcess.");
            return;
        }

        try (transport) {
            printSnapshot(observeFor(transport, SNAPSHOT_WINDOW));
        }
    }

    private static Snapshot observeFor(SocketClientTaskTransport transport, Duration window) {
        AtomicReference<TaskEvent> latest = new AtomicReference<>();
        AtomicReference<Integer> lastProgress = new AtomicReference<>();
        AtomicInteger observed = new AtomicInteger();

        ListenerHandle handle = new ListenerRegistration(transport)
                .taskName(ExampleConfig.TASK_NAME)
                .onEvent(event -> {
                    latest.set(event);
                    if (event.progress() != null) {
                        lastProgress.set(event.progress());
                    }
                    observed.incrementAndGet();
                })
                .start();

        sleep(window);
        handle.stop();

        return new Snapshot(latest.get(), lastProgress.get(), observed.get());
    }

    private static void printSnapshot(Snapshot snapshot) {
        TaskEvent latest = snapshot.latestEvent();
        if (latest == null) {
            System.out.println("Nessun evento dal task nella finestra di osservazione: "
                    + "probabilmente non in esecuzione.");
            return;
        }

        String stato = latest.type().isTerminal()
                ? "terminato (" + latest.type() + ")"
                : "in esecuzione (RUNNING)";

        System.out.println("--- Fotografia dello stato del task ---");
        System.out.println("Task:                 " + latest.taskName());
        System.out.println("Execution:            " + latest.executionId());
        System.out.println("Stato:                " + stato);
        System.out.println("Avanzamento:          "
                + (snapshot.lastProgress() != null ? snapshot.lastProgress() + "%" : "n/d"));
        System.out.println("Ultimo evento visto:  " + latest.type() + " #" + latest.sequenceNumber());
        System.out.println("Eventi nella finestra: " + snapshot.observedCount());
        System.out.println("Il client termina ora; il task prosegue.");
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while taking the snapshot", interrupted);
        }
    }

    private record Snapshot(TaskEvent latestEvent, Integer lastProgress, int observedCount) {
    }
}
