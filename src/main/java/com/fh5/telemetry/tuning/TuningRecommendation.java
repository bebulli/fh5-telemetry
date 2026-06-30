package com.fh5.telemetry.tuning;

import java.util.List;

public record TuningRecommendation(
        TuningStyle style,
        AxlePair tirePressurePsi,
        GearingAdvice gearing,
        AxlePair camberDegrees,
        AxlePair toeDegrees,
        float frontCasterDegrees,
        AxlePair rideHeightMm,
        AxlePair aeroLevel,
        float brakeBalanceFrontPct,
        float brakePressurePct,
        float diffAccelLockPct,
        float diffDecelLockPct,
        AxlePair antiRollBarStiffness,
        AxlePair springRateNmm,
        AxlePair reboundDamping,
        AxlePair bumpDamping,
        List<String> notes) {
}
