package com.fh5.telemetry.model;

/**
 * Whether the car is sitting still or actually moving. Tuning heuristics
 * only make sense on driving samples; static samples are useful as a
 * baseline (cold tire temps, resting suspension travel) but would skew
 * slip/temp averages if mixed in.
 */
public enum DrivingState {
    STATIC,
    DRIVING;

    private static final float MOVING_THRESHOLD_MPS = 1.0f;

    public static DrivingState fromSpeed(float speedMps) {
        return Math.abs(speedMps) >= MOVING_THRESHOLD_MPS ? DRIVING : STATIC;
    }
}
