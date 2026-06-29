package org.tasktelemetry.example.clientservercrossprocess.example1;

import java.io.IOException;

import org.tasktelemetry.transport.crossprocess.CrossProcessTaskHub;

/**
 * Hub process: starts the localhost relay that the producer and the consumer
 * connect to. Start it first and leave it running; press Enter to stop it.
 */
public final class HubProcess {

    private HubProcess() {
    }

    public static void main(String[] args) throws IOException {
        try (CrossProcessTaskHub hub = new CrossProcessTaskHub(ExampleConfig.PORT)) {
            System.out.println("Hub listening on " + ExampleConfig.HOST + ":" + hub.port()
                    + " - press Enter to stop");
            int read = System.in.read();

            System.out.println("Letti: " + read);
            System.out.println("Hub stopped");
        }
    }
}
