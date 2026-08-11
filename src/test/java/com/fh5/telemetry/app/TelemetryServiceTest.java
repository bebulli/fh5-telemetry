package com.fh5.telemetry.app;

import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.sample.SamplePacketBuilder;
import com.fh5.telemetry.tuning.CarSpec;
import com.fh5.telemetry.tuning.TuningRecommendation;
import com.fh5.telemetry.tuning.TuningStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServiceTest {

    private TelemetryService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.stopListening();
        }
    }

    private void send(int port, byte[] packet) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(packet, packet.length, InetAddress.getByName("127.0.0.1"), port));
        }
        Thread.sleep(200);
    }

    @Test
    void performanceIndexChangeResetsTheSampleWindow(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        service.startListening("127.0.0.1", 0);
        int port = service.boundPort();

        byte[] carA = new SamplePacketBuilder().carPerformanceIndex(700).speedMps(30f).buildDash();
        send(port, carA);
        send(port, carA);
        assertTrue(service.sampleSummary().isPresent());
        int countBeforeSwap = service.sampleSummary().get().sampleCount();
        assertEquals(2, countBeforeSwap);

        byte[] carB = new SamplePacketBuilder().carPerformanceIndex(900).speedMps(30f).buildDash();
        send(port, carB);

        assertEquals(1, service.sampleSummary().get().sampleCount());
    }

    @Test
    void sameCarAcrossMenuVisitsDoesNotResetTheSampleWindow(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        service.startListening("127.0.0.1", 0);
        int port = service.boundPort();

        byte[] driving = new SamplePacketBuilder().carPerformanceIndex(700).speedMps(30f).buildDash();
        byte[] menu = new SamplePacketBuilder().carPerformanceIndex(0).speedMps(0f).isRaceOn(false).buildDash();

        send(port, driving);
        send(port, menu);
        send(port, driving);

        assertEquals(2, service.sampleSummary().get().sampleCount());
    }

    @Test
    void repeatedUnresolvedIssueGetsFlaggedInNotes(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        service.startListening("127.0.0.1", 0);
        int port = service.boundPort();

        byte[] stillUndersteering = new SamplePacketBuilder()
                .carPerformanceIndex(700)
                .speedMps(30f)
                .tireSlipAngle(0.3f, 0.3f, 0.05f, 0.05f)
                .buildDash();
        send(port, stillUndersteering);

        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 700);
        TuningRecommendation first = service.computeTuning(spec, TuningStyle.GRIP, Set.of()).orElseThrow();
        assertTrue(first.notes().stream().noneMatch(n -> n.contains("Attempt #")));

        send(port, stillUndersteering);
        TuningRecommendation second = service.computeTuning(spec, TuningStyle.GRIP, Set.of()).orElseThrow();
        assertTrue(second.notes().stream().anyMatch(n -> n.contains("Attempt #2")));
    }

    @Test
    void resetPeaksClearsTuningHistory(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        service.startListening("127.0.0.1", 0);
        int port = service.boundPort();

        byte[] stillUndersteering = new SamplePacketBuilder()
                .carPerformanceIndex(700)
                .speedMps(30f)
                .tireSlipAngle(0.3f, 0.3f, 0.05f, 0.05f)
                .buildDash();
        send(port, stillUndersteering);

        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 700);
        service.computeTuning(spec, TuningStyle.GRIP, Set.of());

        assertTrue(service.resetPeaks());
        send(port, stillUndersteering);
        TuningRecommendation afterReset = service.computeTuning(spec, TuningStyle.GRIP, Set.of()).orElseThrow();

        assertTrue(afterReset.notes().stream().noneMatch(n -> n.contains("Attempt #")));
    }

    @Test
    void resetPeaksIsANoOpWhenNothingToReset(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        assertFalse(service.resetPeaks());
    }

    @Test
    void readRecordingSummaryReflectsRecordedSamples(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        service.startListening("127.0.0.1", 0);
        int port = service.boundPort();

        String filename = service.startRecording("test");
        byte[] driving = new SamplePacketBuilder().carPerformanceIndex(700).drivetrainType(1).speedMps(40f).buildDash();
        send(port, driving);
        send(port, driving);
        service.stopRecording();

        RecordingSummary summary = service.readRecordingSummary(filename);

        assertEquals(filename, summary.file());
        assertEquals(2, summary.totalPacketCount());
        assertEquals(700, summary.carPerformanceIndex());
        assertEquals(DrivetrainType.RWD, summary.drivetrain());
        assertTrue(summary.drivingSummary().isPresent());
        assertEquals(2, summary.drivingSummary().get().sampleCount());
    }

    @Test
    void readRecordingWindowFiltersByElapsedTime(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        service.startListening("127.0.0.1", 0);
        int port = service.boundPort();

        String filename = service.startRecording("window-test");
        byte[] driving = new SamplePacketBuilder().carPerformanceIndex(700).speedMps(40f).buildDash();
        send(port, driving);
        send(port, driving);
        send(port, driving);
        service.stopRecording();

        List<RecordedSample> all = service.readRecordingWindow(filename, 0, Long.MAX_VALUE);
        assertEquals(3, all.size());

        List<RecordedSample> tailOnly = service.readRecordingWindow(filename, all.get(1).elapsedMillis(), Long.MAX_VALUE);
        assertEquals(2, tailOnly.size());
    }

    @Test
    void readRecordingSummaryFailsForUnknownFile(@TempDir Path tempDir) throws Exception {
        service = new TelemetryService(tempDir.resolve("recordings"));
        assertThrows(IOException.class, () -> service.readRecordingSummary("does-not-exist.fh5rec"));
    }
}
