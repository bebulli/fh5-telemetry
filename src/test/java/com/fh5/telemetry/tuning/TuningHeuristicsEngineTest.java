package com.fh5.telemetry.tuning;

import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.parser.TelemetryParser;
import com.fh5.telemetry.sample.SamplePacketBuilder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertTrue(heavyResult.springRateLbsPerIn().front() > lightResult.springRateLbsPerIn().front());
    }

    @Test
    void arbStiffnessStaysWithinGameSliderRange() {
        CarSpec spec = new CarSpec(1800f, DrivetrainType.RWD, 700f, 900);
        TuningRecommendation result = engine.recommend(spec, summaryWithTireTemps(85f, 82f), TuningStyle.DRIFT);

        assertTrue(result.antiRollBarStiffness().front() >= 1f && result.antiRollBarStiffness().front() <= 65f);
        assertTrue(result.antiRollBarStiffness().rear() >= 1f && result.antiRollBarStiffness().rear() <= 65f);
    }

    @Test
    void aggregatorIgnoresStaticSamples() {
        TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();
        byte[] parked = new SamplePacketBuilder().speedMps(0f).buildDash();
        aggregator.add(parser.parse(parked, parked.length));

        assertEquals(0, aggregator.sampleCount());
    }
}
