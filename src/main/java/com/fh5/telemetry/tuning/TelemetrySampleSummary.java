package com.fh5.telemetry.tuning;

import com.fh5.telemetry.model.Corners;

import java.util.Optional;

public record TelemetrySampleSummary(
        int sampleCount,
        Corners avgTireSlipRatio,
        Corners avgTireSlipAngle,
        Corners avgSuspensionTravelNormalized,
        Optional<Corners> avgTireTempCelsius,
        float topSpeedMps) {
}
