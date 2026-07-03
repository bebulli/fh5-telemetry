package com.fh5.telemetry.tuning;

import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.parser.TelemetryParser;
import com.fh5.telemetry.sample.SamplePacketBuilder;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuningHeuristicsEngineTest {

    private final TelemetryParser parser = new TelemetryParser();
    private final TuningHeuristicsEngine engine = new TuningHeuristicsEngine();

    private TelemetrySampleSummary summaryWithTireTemps(float frontTemp, float rearTemp) {
        TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();
        for (int i = 0; i < 10; i++) {
            byte[] raw = new SamplePacketBuilder()
                    .speedMps(30f)
                    .tireSlipRatio(0.05f, 0.05f, 0.03f, 0.03f)
                    .tireSlipAngle(0.1f, 0.1f, 0.1f, 0.1f)
                    .tireTemp(frontTemp, frontTemp, rearTemp, rearTemp)
                    .buildDash();
            TelemetryPacket packet = parser.parse(raw, raw.length);
            aggregator.add(packet);
        }
        Optional<TelemetrySampleSummary> summary = aggregator.summarize();
        assertTrue(summary.isPresent());
        return summary.get();
    }

    @Test
    void gripTiresPressureStaysWithinSafeRange() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 800);
        TuningRecommendation result = engine.recommend(spec, summaryWithTireTemps(85f, 82f), TuningStyle.GRIP);

        assertTrue(result.tirePressurePsi().front() >= 20f && result.tirePressurePsi().front() <= 45f);
        assertTrue(result.tirePressurePsi().rear() >= 20f && result.tirePressurePsi().rear() <= 45f);
    }

    @Test
    void overheatingTiresGetLowerPressureThanNormalTemps() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 800);
        TuningRecommendation normal = engine.recommend(spec, summaryWithTireTemps(85f, 82f), TuningStyle.GRIP);
        TuningRecommendation hot = engine.recommend(spec, summaryWithTireTemps(110f, 105f), TuningStyle.GRIP);

        assertTrue(hot.tirePressurePsi().front() < normal.tirePressurePsi().front());
        assertTrue(hot.tirePressurePsi().rear() < normal.tirePressurePsi().rear());
    }

    @Test
    void driftSetupLowersFrontPressureAndRaisesRearPressureRelativeToGrip() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.RWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation grip = engine.recommend(spec, summary, TuningStyle.GRIP);
        TuningRecommendation drift = engine.recommend(spec, summary, TuningStyle.DRIFT);

        assertTrue(drift.tirePressurePsi().front() < grip.tirePressurePsi().front());
        assertTrue(drift.tirePressurePsi().rear() > grip.tirePressurePsi().rear());
    }

    @Test
    void driftSetupLoosensRearCamberRelativeToGrip() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.RWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation grip = engine.recommend(spec, summary, TuningStyle.GRIP);
        TuningRecommendation drift = engine.recommend(spec, summary, TuningStyle.DRIFT);

        // Drift wants a looser (less negative) rear and more aggressive front.
        assertTrue(drift.camberDegrees().rear() > grip.camberDegrees().rear());
        assertTrue(drift.camberDegrees().front() < grip.camberDegrees().front());
    }

    @Test
    void heavierCarGetsStifferSprings() {
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);
        CarSpec light = new CarSpec(1200f, DrivetrainType.AWD, 550f, 800);
        CarSpec heavy = new CarSpec(2200f, DrivetrainType.AWD, 550f, 800);

        TuningRecommendation lightResult = engine.recommend(light, summary, TuningStyle.GRIP);
        TuningRecommendation heavyResult = engine.recommend(heavy, summary, TuningStyle.GRIP);

        assertTrue(heavyResult.springRateNmm().front() > lightResult.springRateNmm().front());
    }

    @Test
    void arbStiffnessStaysWithinGameSliderRange() {
        CarSpec spec = new CarSpec(1800f, DrivetrainType.RWD, 700f, 900);
        TuningRecommendation result = engine.recommend(spec, summaryWithTireTemps(85f, 82f), TuningStyle.DRIFT);

        assertTrue(result.antiRollBarStiffness().front() >= 1f && result.antiRollBarStiffness().front() <= 65f);
        assertTrue(result.antiRollBarStiffness().rear() >= 1f && result.antiRollBarStiffness().rear() <= 65f);
    }

    @Test
    void reportedUndersteerAddsFrontCamberBeyondTelemetryAlone() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation withoutSymptom = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of());
        TuningRecommendation withUndersteer = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of(DrivingSymptom.UNDERSTEER));

        assertTrue(withUndersteer.camberDegrees().front() < withoutSymptom.camberDegrees().front());
        assertTrue(withUndersteer.antiRollBarStiffness().front() < withoutSymptom.antiRollBarStiffness().front());
    }

    @Test
    void reportedTractionLossSoftensRearEnd() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.RWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation withoutSymptom = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of());
        TuningRecommendation withTractionLoss = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of(DrivingSymptom.TRACTION_LOSS));

        assertTrue(withTractionLoss.tirePressurePsi().rear() < withoutSymptom.tirePressurePsi().rear());
        assertTrue(withTractionLoss.springRateNmm().rear() < withoutSymptom.springRateNmm().rear());
    }

    @Test
    void reportedBouncySuspensionStiffensBothAxles() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation withoutSymptom = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of());
        TuningRecommendation withBounce = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of(DrivingSymptom.BOUNCY_SUSPENSION));

        assertTrue(withBounce.springRateNmm().front() > withoutSymptom.springRateNmm().front());
        assertTrue(withBounce.springRateNmm().rear() > withoutSymptom.springRateNmm().rear());
    }

    @Test
    void springRateStaysWithinRealGameBounds() {
        CarSpec light = new CarSpec(900f, DrivetrainType.FWD, 200f, 200);
        CarSpec heavy = new CarSpec(2400f, DrivetrainType.AWD, 1200f, 999);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation lightResult = engine.recommend(light, summary, TuningStyle.GRIP);
        TuningRecommendation heavyResult = engine.recommend(heavy, summary, TuningStyle.DRIFT);

        for (TuningRecommendation result : new TuningRecommendation[]{lightResult, heavyResult}) {
            assertTrue(result.springRateNmm().front() >= 528.3f && result.springRateNmm().front() <= 2641.3f);
            assertTrue(result.springRateNmm().rear() >= 528.3f && result.springRateNmm().rear() <= 2641.3f);
        }
    }

    @Test
    void newTuningFieldsStayWithinPlausibleRanges() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.RWD, 550f, 800);
        TuningRecommendation result = engine.recommend(spec, summaryWithTireTemps(85f, 82f), TuningStyle.GRIP);

        assertTrue(result.frontCasterDegrees() >= 1f && result.frontCasterDegrees() <= 7f);
        assertTrue(result.rideHeightLevel().front() >= 0f && result.rideHeightLevel().front() <= 10f);
        assertTrue(result.aeroKgf().front() >= 122f && result.aeroKgf().front() <= 267f);
        assertTrue(result.brakeBalanceFrontPct() >= 25f && result.brakeBalanceFrontPct() <= 75f);
        assertTrue(result.brakePressurePct() >= 50f && result.brakePressurePct() <= 200f);
        assertTrue(result.diffAccelLockPct() >= 0f && result.diffAccelLockPct() <= 100f);
        assertTrue(result.diffDecelLockPct() >= 0f && result.diffDecelLockPct() <= 100f);
        assertTrue(result.rearDiffAccelLockPct().isEmpty());
        assertTrue(result.centerDiffRearBiasPct().isEmpty());
    }

    @Test
    void driftReducesAeroAndIncreasesDiffLockRelativeToGrip() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.RWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation grip = engine.recommend(spec, summary, TuningStyle.GRIP);
        TuningRecommendation drift = engine.recommend(spec, summary, TuningStyle.DRIFT);

        assertTrue(drift.aeroKgf().rear() < grip.aeroKgf().rear());
        assertTrue(drift.diffAccelLockPct() > grip.diffAccelLockPct());
        assertTrue(drift.diffDecelLockPct() > grip.diffDecelLockPct());
    }

    @Test
    void awdCarsGetSeparateFrontRearDiffAndCenterSplit() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 800);
        TuningRecommendation result = engine.recommend(spec, summaryWithTireTemps(85f, 82f), TuningStyle.GRIP);

        assertTrue(result.rearDiffAccelLockPct().isPresent());
        assertTrue(result.rearDiffDecelLockPct().isPresent());
        assertTrue(result.centerDiffRearBiasPct().isPresent());
        assertTrue(result.rearDiffAccelLockPct().get() > result.diffAccelLockPct());
    }

    @Test
    void fwdUndersteerLoosensAccelDiffLock() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.FWD, 300f, 500);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation withoutSymptom = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of());
        TuningRecommendation withUndersteer = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of(DrivingSymptom.UNDERSTEER));

        assertTrue(withUndersteer.diffAccelLockPct() < withoutSymptom.diffAccelLockPct());
    }

    @Test
    void rwdOversteerLoosensDecelDiffLock() {
        CarSpec spec = new CarSpec(1500f, DrivetrainType.RWD, 550f, 800);
        TelemetrySampleSummary summary = summaryWithTireTemps(85f, 82f);

        TuningRecommendation withoutSymptom = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of());
        TuningRecommendation withOversteer = engine.recommend(spec, summary, TuningStyle.GRIP, Set.of(DrivingSymptom.OVERSTEER));

        assertTrue(withOversteer.diffDecelLockPct() < withoutSymptom.diffDecelLockPct());
    }

    @Test
    void aggregatorIgnoresStaticSamples() {
        TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();
        byte[] parked = new SamplePacketBuilder().speedMps(0f).buildDash();
        aggregator.add(parser.parse(parked, parked.length));

        assertEquals(0, aggregator.sampleCount());
    }

    @Test
    void resetPeaksClearsOnlyTopSpeedAndPeakPowerNotSlipAverages() {
        TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();
        byte[] raw = new SamplePacketBuilder().speedMps(40f).tireSlipRatio(0.1f, 0.1f, 0.1f, 0.1f).buildDash();
        aggregator.add(parser.parse(raw, raw.length));

        assertTrue(aggregator.resetPeaks());
        TelemetrySampleSummary summary = aggregator.summarize().orElseThrow();
        assertEquals(0f, summary.topSpeedMps());
        assertEquals(1, summary.sampleCount());
        assertEquals(0.1f, summary.avgTireSlipRatio().frontLeft(), 0.001f);
    }

    @Test
    void resetPeaksIsANoOpWhenAlreadyZero() {
        TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();
        assertFalse(aggregator.resetPeaks());
    }
}
