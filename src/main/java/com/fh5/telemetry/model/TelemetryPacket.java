package com.fh5.telemetry.model;

import java.util.Optional;

public record TelemetryPacket(
        boolean isRaceOn,
        long timestampMs,
        float engineMaxRpm,
        float engineIdleRpm,
        float currentEngineRpm,
        Vector3 acceleration,
        Vector3 velocity,
        Vector3 angularVelocity,
        float yaw,
        float pitch,
        float roll,
        Corners suspensionTravelNormalized,
        Corners tireSlipRatio,
        Corners wheelRotationSpeed,
        Corners tireSlipAngle,
        Corners tireCombinedSlip,
        Corners suspensionTravelMeters,
        int carOrdinal,
        int carClass,
        int carPerformanceIndex,
        DrivetrainType drivetrain,
        int numCylinders,
        Optional<DashData> dash) {

    public boolean isDash() {
        return dash.isPresent();
    }

    /**
     * Speed magnitude in m/s. Dash packets report this directly; Sled-only
     * packets fall back to the velocity vector's magnitude.
     */
    public float speedMps() {
        return dash.map(DashData::speedMps).orElseGet(() ->
                (float) Math.sqrt(velocity.x() * velocity.x()
                        + velocity.y() * velocity.y()
                        + velocity.z() * velocity.z()));
    }

    public DrivingState drivingState() {
        if (!isRaceOn) {
            return DrivingState.STATIC;
        }
        return DrivingState.fromSpeed(speedMps());
    }
}
