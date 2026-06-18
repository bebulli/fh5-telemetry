package com.fh5.telemetry.model;

public enum DrivetrainType {
    FWD, RWD, AWD, UNKNOWN;

    public static DrivetrainType fromWireValue(int value) {
        return switch (value) {
            case 0 -> FWD;
            case 1 -> RWD;
            case 2 -> AWD;
            default -> UNKNOWN;
        };
    }
}
