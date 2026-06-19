package com.fh5.telemetry.tuning;

import java.util.List;

public record TuningRecommendation(
        TuningStyle style,
        AxlePair tirePressurePsi,
        GearingAdvice gearing,
        AxlePair camberDegrees,
        AxlePair toeDegrees,
        AxlePair antiRollBarStiffness,
        AxlePair springRateLbsPerIn,
        AxlePair reboundDamping,
        AxlePair bumpDamping,
        List<String> notes) {
}
