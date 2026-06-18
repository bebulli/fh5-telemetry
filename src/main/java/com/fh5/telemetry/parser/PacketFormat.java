package com.fh5.telemetry.parser;

/**
 * Forza's "Data Out" feed comes in two shapes. Sled is the older, smaller
 * layout (physics only). Dash is a superset that adds dashboard fields
 * (speed, tire temps, lap timing, pedal inputs) after the Sled fields.
 * FH5 always sends the Dash-sized packet; Sled support is kept because it
 * shares almost all of its layout and some older captures/tools use it.
 */
public enum PacketFormat {
    SLED(232),
    DASH(324);

    private final int byteLength;

    PacketFormat(int byteLength) {
        this.byteLength = byteLength;
    }

    public int byteLength() {
        return byteLength;
    }

    public static PacketFormat fromLength(int length) {
        for (PacketFormat format : values()) {
            if (format.byteLength == length) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unrecognized packet length: " + length
                + " (expected " + SLED.byteLength + " for Sled or " + DASH.byteLength + " for Dash)");
    }
}
