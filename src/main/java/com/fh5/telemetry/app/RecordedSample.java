package com.fh5.telemetry.app;

import com.fh5.telemetry.model.TelemetryPacket;

public record RecordedSample(long elapsedMillis, TelemetryPacket packet) {
}
