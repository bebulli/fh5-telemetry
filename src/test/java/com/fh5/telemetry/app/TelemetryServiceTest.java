package com.fh5.telemetry.app;

import com.fh5.telemetry.sample.SamplePacketBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
