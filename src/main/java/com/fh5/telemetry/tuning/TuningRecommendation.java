package com.fh5.telemetry.tuning;

import java.util.List;
import java.util.Optional;

public record TuningRecommendation(
        TuningStyle style,
        AxlePair tirePressurePsi,
        GearingAdvice gearing,
        AxlePair camberDegrees,
        AxlePair toeDegrees,
        float frontCasterDegrees,
        AxlePair rideHeightLevel,
        AxlePair aeroKgf,
        float brakeBalanceFrontPct,
        float brakePressurePct,
        float diffAccelLockPct,
        float diffDecelLockPct,
        Optional<Float> rearDiffAccelLockPct,
        Optional<Float> rearDiffDecelLockPct,
        Optional<Float> centerDiffRearBiasPct,
        AxlePair antiRollBarStiffness,
        AxlePair springRateNmm,
        AxlePair reboundDamping,
        AxlePair bumpDamping,
        List<String> notes) {
}
