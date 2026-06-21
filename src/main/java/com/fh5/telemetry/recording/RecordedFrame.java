package com.fh5.telemetry.recording;

public record RecordedFrame(long elapsedMillis, byte[] data) {
}
