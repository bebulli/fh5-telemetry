package com.fh5.telemetry.model;

/**
 * Fields only present in the Dash packet format, not in Sled.
 */
public record DashData(
        Vector3 position,
        float speedMps,
        float powerWatts,
        float torqueNm,
        Corners tireTempCelsius,
        float boost,
        float fuel,
        float distanceTraveledMeters,
        float bestLapSeconds,
        float lastLapSeconds,
        float currentLapSeconds,
        float currentRaceTimeSeconds,
        int lap,
        int racePosition,
        int accel,
        int brake,
        int clutch,
        int handBrake,
        int gear,
        int steer,
        int normalizedDrivingLine,
        int normalizedAiBrakeDifference) {
}
