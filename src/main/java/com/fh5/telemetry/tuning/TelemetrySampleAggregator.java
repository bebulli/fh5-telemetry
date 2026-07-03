package com.fh5.telemetry.tuning;

import com.fh5.telemetry.model.Corners;
import com.fh5.telemetry.model.DrivingState;
import com.fh5.telemetry.model.TelemetryPacket;

import java.util.Optional;

/**
 * Accumulates running averages of the fields the tuning engine cares about.
 * Static (parked/idle) packets are ignored so they don't drag slip and
 * suspension-travel averages toward zero.
 */
public final class TelemetrySampleAggregator {

    private int count;
    private float slipRatioFL, slipRatioFR, slipRatioRL, slipRatioRR;
    private float slipAngleFL, slipAngleFR, slipAngleRL, slipAngleRR;
    private float suspFL, suspFR, suspRL, suspRR;
    private float tireTempFL, tireTempFR, tireTempRL, tireTempRR;
    private int dashCount;
    private float topSpeedMps;
    private float peakPowerWatts;

    public synchronized void add(TelemetryPacket packet) {
        if (packet.drivingState() != DrivingState.DRIVING) {
            return;
        }
        count++;

        Corners slipRatio = packet.tireSlipRatio();
        slipRatioFL += slipRatio.frontLeft();
        slipRatioFR += slipRatio.frontRight();
        slipRatioRL += slipRatio.rearLeft();
        slipRatioRR += slipRatio.rearRight();

        Corners slipAngle = packet.tireSlipAngle();
        slipAngleFL += slipAngle.frontLeft();
        slipAngleFR += slipAngle.frontRight();
        slipAngleRL += slipAngle.rearLeft();
        slipAngleRR += slipAngle.rearRight();

        Corners susp = packet.suspensionTravelNormalized();
        suspFL += susp.frontLeft();
        suspFR += susp.frontRight();
        suspRL += susp.rearLeft();
        suspRR += susp.rearRight();

        packet.dash().ifPresent(dash -> {
            Corners temp = dash.tireTempCelsius();
            tireTempFL += temp.frontLeft();
            tireTempFR += temp.frontRight();
            tireTempRL += temp.rearLeft();
            tireTempRR += temp.rearRight();
            dashCount++;
            peakPowerWatts = Math.max(peakPowerWatts, dash.powerWatts());
        });

        topSpeedMps = Math.max(topSpeedMps, packet.speedMps());
    }

    public synchronized int sampleCount() {
        return count;
    }

    public synchronized Optional<TelemetrySampleSummary> summarize() {
        if (count == 0) {
            return Optional.empty();
        }

        Optional<Corners> avgTireTemp = dashCount == 0
                ? Optional.empty()
                : Optional.of(new Corners(
                        tireTempFL / dashCount, tireTempFR / dashCount,
                        tireTempRL / dashCount, tireTempRR / dashCount));

        Optional<Float> peakPowerHp = dashCount == 0
                ? Optional.empty()
                : Optional.of(peakPowerWatts / 745.7f);

        return Optional.of(new TelemetrySampleSummary(
                count,
                new Corners(slipRatioFL / count, slipRatioFR / count, slipRatioRL / count, slipRatioRR / count),
                new Corners(slipAngleFL / count, slipAngleFR / count, slipAngleRL / count, slipAngleRR / count),
                new Corners(suspFL / count, suspFR / count, suspRL / count, suspRR / count),
                avgTireTemp,
                topSpeedMps,
                peakPowerHp));
    }

    public synchronized void reset() {
        count = 0;
        dashCount = 0;
        slipRatioFL = slipRatioFR = slipRatioRL = slipRatioRR = 0;
        slipAngleFL = slipAngleFR = slipAngleRL = slipAngleRR = 0;
        suspFL = suspFR = suspRL = suspRR = 0;
        tireTempFL = tireTempFR = tireTempRL = tireTempRR = 0;
        topSpeedMps = 0;
        peakPowerWatts = 0;
    }

    /**
     * Resets only the peak power and top speed, leaving the running slip/temp
     * averages alone. Useful after a pull to see a fresh peak without losing
     * the rest of the sample window.
     *
     * @return true if there was anything to reset
     */
    public synchronized boolean resetPeaks() {
        boolean hadPeaks = topSpeedMps > 0 || peakPowerWatts > 0;
        topSpeedMps = 0;
        peakPowerWatts = 0;
        return hadPeaks;
    }
}
