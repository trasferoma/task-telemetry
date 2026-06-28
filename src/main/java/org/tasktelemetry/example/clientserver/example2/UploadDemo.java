package org.tasktelemetry.example.clientserver.example2;

import java.time.Duration;

/**
 * Runnable demo wiring the example2 producer and client.
 *
 * <p>It starts {@link TaskUpload} on a background thread (the "upload process"),
 * gives it a short head start so it is already in progress, then runs the
 * {@link UploadClient}, which detects the running upload, waits for it to finish
 * and meanwhile checks that the task's heart keeps beating.
 */
public final class UploadDemo {

    private static final Duration HEAD_START = Duration.ofSeconds(1);

    private UploadDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        Thread uploadProcess = new Thread(() -> new TaskUpload().upload("foto.zip"), "upload-process");
        uploadProcess.start();

        Thread.sleep(HEAD_START.toMillis());

        new UploadClient().run();

        uploadProcess.join();
    }
}
