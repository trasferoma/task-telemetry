package org.tasktelemetry.example;

import org.tasktelemetry.TaskEvent;
import org.tasktelemetry.TaskReporter;
import org.tasktelemetry.TaskTelemetry;

/**
 * Minimal pure-Java usage example (no framework): a runtime with the default
 * in-memory transport, a filtered listener that prints events, and a simulated
 * import that reports progress through a {@link TaskReporter}.
 */
public final class PureJavaExample {

    private static final String TASK_NAME = "IMPORT_CLIENTI";
    private static final String INPUT_FILE = "file-import-clienti-2026.csv";

    private PureJavaExample() {
    }

    public static void main(String[] args) {
        try (TaskTelemetry telemetry = TaskTelemetry.defaults()) {
            telemetry.listen()
                    .taskName(TASK_NAME)
                    .onEvent(PureJavaExample::printEvent)
                    .start();

            runImport(telemetry);
        }
    }

    private static void runImport(TaskTelemetry telemetry) {
        try (TaskReporter reporter = telemetry.start(TASK_NAME, INPUT_FILE)) {
            reporter.progress(0, "Import started");
            reporter.info("Reading input file");
            reporter.progress(50, "Half of the file processed");
            reporter.progress(100, "Processing finished");
            reporter.completed("Import completed");
        }
    }

    private static void printEvent(TaskEvent event) {
        StringBuilder line = new StringBuilder();
        line.append(event.type()).append(" #").append(event.sequenceNumber());

        if (event.progress() != null) {
            line.append(" (").append(event.progress()).append("%)");
        }
        if (event.message() != null) {
            line.append(" - ").append(event.message());
        }

        System.out.println(line);
    }
}
