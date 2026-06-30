package com.fh5.telemetry.app;

import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.net.UdpListener;
import com.fh5.telemetry.parser.TelemetryParser;
import com.fh5.telemetry.recording.RecordedFrame;
import com.fh5.telemetry.recording.SessionReader;
import com.fh5.telemetry.recording.SessionRecorder;
import com.fh5.telemetry.tuning.CarSpec;
import com.fh5.telemetry.tuning.DrivingSymptom;
import com.fh5.telemetry.tuning.TelemetrySampleAggregator;
import com.fh5.telemetry.tuning.TelemetrySampleSummary;
import com.fh5.telemetry.tuning.TuningHeuristicsEngine;
import com.fh5.telemetry.tuning.TuningRecommendation;
import com.fh5.telemetry.tuning.TuningStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Wires the UDP listener, parser, tuning engine and recorder together and
 * holds the app's live state. This is what both the CLI and the HTTP API
 * talk to.
 */
public final class TelemetryService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final TelemetryParser parser = new TelemetryParser();
    private final UdpListener listener = new UdpListener();
    private final TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();
    private final TuningHeuristicsEngine tuningEngine = new TuningHeuristicsEngine();
    private final Path recordingsDir;

    private volatile TelemetryPacket latestPacket;
    private final AtomicLong packetsReceived = new AtomicLong();
    private volatile SessionRecorder activeRecorder;
    private volatile String activeRecordingFile;
    private volatile Thread replayThread;
    private volatile int lastKnownPerformanceIndex;

    public TelemetryService(Path recordingsDir) throws IOException {
        Files.createDirectories(recordingsDir);
        this.recordingsDir = recordingsDir;
    }

    public void startListening(String bindAddress, int port) throws IOException {
        listener.start(bindAddress, port, this::onPacket);
    }

    public void stopListening() {
        listener.stop();
    }

    public boolean isListening() {
        return listener.isRunning();
    }

    public String boundAddress() {
        return listener.boundAddress();
    }

    public int boundPort() {
        return listener.boundPort();
    }

    public long packetsReceived() {
        return packetsReceived.get();
    }

    public Optional<TelemetryPacket> latestPacket() {
        return Optional.ofNullable(latestPacket);
    }

    public Optional<TelemetrySampleSummary> sampleSummary() {
        return aggregator.summarize();
    }

    public void resetSample() {
        aggregator.reset();
    }

    public boolean isRecording() {
        return activeRecorder != null;
    }

    public Optional<String> activeRecordingFile() {
        return Optional.ofNullable(activeRecordingFile);
    }

    public synchronized String startRecording(String name) throws IOException {
        if (activeRecorder != null) {
            stopRecording();
        }
        String base = (name == null || name.isBlank()) ? "session" : sanitize(name);
        String filename = base + "-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".fh5rec";
        activeRecorder = new SessionRecorder(recordingsDir.resolve(filename));
        activeRecordingFile = filename;
        return filename;
    }

    public synchronized Optional<RecordingResult> stopRecording() throws IOException {
        if (activeRecorder == null) {
            return Optional.empty();
        }
        int count = activeRecorder.packetsRecorded();
        String file = activeRecordingFile;
        activeRecorder.close();
        activeRecorder = null;
        activeRecordingFile = null;
        return Optional.of(new RecordingResult(file, count));
    }

    public List<String> listRecordings() throws IOException {
        try (Stream<Path> files = Files.list(recordingsDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".fh5rec"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    /**
     * Replays a recorded session on a background thread, feeding frames
     * through the same pipeline as live packets (so the UI and tuning
     * aggregator see it as if it were happening now) at its original pacing.
     */
    public synchronized void replay(String filename) throws IOException {
        if (replayThread != null && replayThread.isAlive()) {
            throw new IllegalStateException("A replay is already in progress");
        }
        Path file = recordingsDir.resolve(filename);
        if (!Files.exists(file)) {
            throw new IOException("No such recording: " + filename);
        }

        replayThread = new Thread(() -> runReplay(file), "session-replay");
        replayThread.setDaemon(true);
        replayThread.start();
    }

    public boolean isReplaying() {
        Thread t = replayThread;
        return t != null && t.isAlive();
    }

    private void runReplay(Path file) {
        try (SessionReader reader = new SessionReader(file)) {
            long replayStartNanos = System.nanoTime();
            Optional<RecordedFrame> frame;
            while ((frame = reader.readNext()).isPresent()) {
                RecordedFrame f = frame.get();
                long targetNanos = replayStartNanos + f.elapsedMillis() * 1_000_000L;
                long waitNanos = targetNanos - System.nanoTime();
                if (waitNanos > 0) {
                    Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
                }
                onPacket(f.data(), f.data().length, "replay:" + file.getFileName());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Replay stopped: " + e.getMessage());
        }
    }

    public Optional<TuningRecommendation> computeTuning(CarSpec spec, TuningStyle style, Set<DrivingSymptom> symptoms) {
        return aggregator.summarize().map(summary -> tuningEngine.recommend(spec, summary, style, symptoms));
    }

    private void onPacket(byte[] data, int length, String senderAddress) {
        try {
            TelemetryPacket packet = parser.parse(data, length);
            latestPacket = packet;
            packetsReceived.incrementAndGet();

            int performanceIndex = packet.carPerformanceIndex();
            if (performanceIndex > 0) {
                if (lastKnownPerformanceIndex > 0 && performanceIndex != lastKnownPerformanceIndex) {
                    // A different PI means a different car (swap or upgrade), the sample
                    // window (including peak power) no longer describes what's being driven now.
                    aggregator.reset();
                }
                lastKnownPerformanceIndex = performanceIndex;
            }

            aggregator.add(packet);

            SessionRecorder recorder = activeRecorder;
            if (recorder != null) {
                recorder.record(data, length);
            }
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Dropped packet from " + senderAddress + ": " + e.getMessage());
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
