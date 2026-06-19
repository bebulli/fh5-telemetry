package com.fh5.telemetry.tuning;

import com.fh5.telemetry.model.DrivetrainType;

/**
 * Car specs entered manually by the user. Forza's telemetry doesn't expose
 * weight, power or PI directly (only car/class ordinals with no lookup
 * table sent over the wire), so these come from the player.
 */
public record CarSpec(
        float weightKg,
        DrivetrainType drivetrain,
        float powerHp,
        int performanceIndex,
        float frontWeightDistributionPct) {

    public CarSpec(float weightKg, DrivetrainType drivetrain, float powerHp, int performanceIndex) {
        this(weightKg, drivetrain, powerHp, performanceIndex, 50f);
    }

    public float powerToWeightHpPerTonne() {
        return powerHp / (weightKg / 1000f);
    }
}
