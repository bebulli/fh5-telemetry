package com.fh5.telemetry.recording;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SessionReader implements AutoCloseable {

    private final DataInputStream in;

    public SessionReader(Path file) throws IOException {
        this.in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
    }

    public Optional<RecordedFrame> readNext() throws IOException {
        try {
            long elapsedMillis = in.readLong();
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);
            return Optional.of(new RecordedFrame(elapsedMillis, data));
        } catch (EOFException e) {
            return Optional.empty();
        }
    }

    public List<RecordedFrame> readAll() throws IOException {
        List<RecordedFrame> frames = new ArrayList<>();
        Optional<RecordedFrame> frame;
        while ((frame = readNext()).isPresent()) {
            frames.add(frame.get());
        }
        return frames;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
