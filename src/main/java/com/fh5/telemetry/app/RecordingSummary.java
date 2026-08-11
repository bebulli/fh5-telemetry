package com.fh5.telemetry.app;

import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.tuning.TelemetrySampleSummary;

import java.util.Optional;

public record RecordingSummary(
        String file,
        long durationMs,
        int totalPacketCount,
        int carOrdinal,
        int carClass,
        int carPerformanceIndex,
        DrivetrainType drivetrain,
        Optional<TelemetrySampleSummary> drivingSummary) {
}
