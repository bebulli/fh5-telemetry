package com.fh5.telemetry.recording;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Appends raw packets to a file as they arrive, each framed with how long
 * after the recording started it was received and its byte length, so a
 * session can be replayed later at its original pacing.
 */
public final class SessionRecorder implements AutoCloseable {

    private final DataOutputStream out;
    private final long startedAtNanos;
    private int packetsRecorded;

    public SessionRecorder(Path file) throws IOException {
        this.out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
        this.startedAtNanos = System.nanoTime();
    }

    public synchronized void record(byte[] data, int length) throws IOException {
        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        out.writeLong(elapsedMillis);
        out.writeInt(length);
        out.write(data, 0, length);
        packetsRecorded++;
    }

    public int packetsRecorded() {
        return packetsRecorded;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
