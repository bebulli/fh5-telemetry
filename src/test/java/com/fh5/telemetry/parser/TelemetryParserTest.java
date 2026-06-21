package com.fh5.telemetry.parser;

import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.sample.SamplePacketBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryParserTest {

    private final TelemetryParser parser = new TelemetryParser();

    @Test
    void parsesDashFields() {
        byte[] raw = new SamplePacketBuilder()
                .currentEngineRpm(6200f)
                .speedMps(50f)
                .gear(4)
                .tireSlipRatio(0.11f, 0.12f, 0.02f, 0.03f)
                .tireTemp(90f, 91f, 70f, 71f)
                .drivetrainType(1)
                .buildDash();

        TelemetryPacket packet = parser.parse(raw, raw.length);

        assertTrue(packet.isDash());
        assertTrue(packet.isRaceOn());
        assertEquals(6200f, packet.currentEngineRpm(), 0.01f);
        assertEquals(DrivetrainType.RWD, packet.drivetrain());
        assertEquals(0.11f, packet.tireSlipRatio().frontLeft(), 0.001f);
        assertEquals(0.03f, packet.tireSlipRatio().rearRight(), 0.001f);

        assertTrue(packet.dash().isPresent());
        assertEquals(50f, packet.dash().get().speedMps(), 0.01f);
        assertEquals(4, packet.dash().get().gear());
        assertEquals(90f, packet.dash().get().tireTempCelsius().frontLeft(), 0.01f);
    }

    @Test
    void parsesSledFieldsWithoutDashData() {
        byte[] raw = new SamplePacketBuilder().currentEngineRpm(4000f).buildSled();

        TelemetryPacket packet = parser.parse(raw, raw.length);

        assertFalse(packet.isDash());
        assertTrue(packet.dash().isEmpty());
        assertEquals(4000f, packet.currentEngineRpm(), 0.01f);
    }

    @Test
    void rejectsUnrecognizedPacketLength() {
        byte[] garbage = new byte[100];
        assertThrows(IllegalArgumentException.class, () -> parser.parse(garbage, garbage.length));
    }

    @Test
    void speedFallsBackToVelocityMagnitudeForSledPackets() {
        byte[] raw = new SamplePacketBuilder().buildSled();
        TelemetryPacket packet = parser.parse(raw, raw.length);

        // Sled packets have no explicit speed field; it's derived from velocity.
        assertEquals(0f, packet.speedMps(), 0.001f);
    }
}
