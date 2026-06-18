package com.fh5.telemetry.model;

/**
 * One value per wheel/corner of the car, in the order Forza always sends
 * them: front left, front right, rear left, rear right.
 */
public record Corners(float frontLeft, float frontRight, float rearLeft, float rearRight) {

    public float frontAverage() {
        return (frontLeft + frontRight) / 2f;
    }

    public float rearAverage() {
        return (rearLeft + rearRight) / 2f;
    }

    public float average() {
        return (frontLeft + frontRight + rearLeft + rearRight) / 4f;
    }
}
