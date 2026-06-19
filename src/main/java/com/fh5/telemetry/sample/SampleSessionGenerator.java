package com.fh5.telemetry.sample;

import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.parser.TelemetryParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a short synthetic driving session (varying speed, RPM, slip and
 * tire temps) so the app can be demoed or tested without the game running.
 */
public final class SampleSessionGenerator {

    private final TelemetryParser parser = new TelemetryParser();

    public List<TelemetryPacket> generate(int tickCount) {
        List<TelemetryPacket> packets = new ArrayList<>(tickCount);
        for (int i = 0; i < tickCount; i++) {
            double phase = i / 20.0;
            float speed = (float) (35 + 20 * Math.sin(phase));
            float rpm = (float) (4500 + 2000 * Math.sin(phase * 1.3));
            float frontSlip = (float) (0.05 + 0.05 * Math.abs(Math.sin(phase * 0.7)));
            float rearSlip = (float) (0.03 + 0.03 * Math.abs(Math.sin(phase * 0.9)));
            float frontTemp = (float) (80 + 15 * Math.abs(Math.sin(phase * 0.5)));
            float rearTemp = (float) (75 + 10 * Math.abs(Math.sin(phase * 0.6)));

            byte[] raw = new SamplePacketBuilder()
                    .speedMps(speed)
                    .currentEngineRpm(rpm)
                    .tireSlipRatio(frontSlip, frontSlip, rearSlip, rearSlip)
                    .tireTemp(frontTemp, frontTemp, rearTemp, rearTemp)
                    .gear((i / 40) % 6 + 1)
                    .buildDash();

            packets.add(parser.parse(raw, raw.length));
        }
        return packets;
    }
}
