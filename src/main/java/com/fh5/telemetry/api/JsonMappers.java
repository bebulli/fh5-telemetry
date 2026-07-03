package com.fh5.telemetry.api;

import com.fh5.telemetry.model.Corners;
import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.model.Vector3;
import com.fh5.telemetry.tuning.AxlePair;
import com.fh5.telemetry.tuning.TelemetrySampleSummary;
import com.fh5.telemetry.tuning.TuningRecommendation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts domain records into the plain Map/List shapes {@link Json} can write. */
final class JsonMappers {

    private JsonMappers() {
    }

    static Map<String, Object> vector3(Vector3 v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", v.x());
        map.put("y", v.y());
        map.put("z", v.z());
        return map;
    }

    static Map<String, Object> corners(Corners c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("frontLeft", c.frontLeft());
        map.put("frontRight", c.frontRight());
        map.put("rearLeft", c.rearLeft());
        map.put("rearRight", c.rearRight());
        return map;
    }

    static Map<String, Object> axlePair(AxlePair pair) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("front", pair.front());
        map.put("rear", pair.rear());
        return map;
    }

    static Map<String, Object> telemetryPacket(TelemetryPacket t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isRaceOn", t.isRaceOn());
        map.put("timestampMs", t.timestampMs());
        map.put("format", t.isDash() ? "DASH" : "SLED");
        map.put("drivingState", t.drivingState().name());
        map.put("speedMph", t.speedMps() * 2.23694f);
        map.put("currentEngineRpm", t.currentEngineRpm());
        map.put("engineMaxRpm", t.engineMaxRpm());
        map.put("acceleration", vector3(t.acceleration()));
        map.put("tireSlipRatio", corners(t.tireSlipRatio()));
        map.put("tireSlipAngle", corners(t.tireSlipAngle()));
        map.put("suspensionTravelNormalized", corners(t.suspensionTravelNormalized()));
        map.put("carOrdinal", t.carOrdinal());
        map.put("carClass", t.carClass());
        map.put("carPerformanceIndex", t.carPerformanceIndex());
        map.put("drivetrain", t.drivetrain().name());

        t.dash().ifPresent(dash -> {
            map.put("gear", dash.gear());
            map.put("powerHp", dash.powerWatts() / 745.7f);
            map.put("torqueNm", dash.torqueNm());
            map.put("fuelPct", dash.fuel() * 100f);
            map.put("tireTempCelsius", corners(dash.tireTempCelsius()));
            map.put("accel", dash.accel());
            map.put("brake", dash.brake());
        });

        return map;
    }

    static Map<String, Object> sampleSummary(TelemetrySampleSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sampleCount", s.sampleCount());
        map.put("avgTireSlipRatio", corners(s.avgTireSlipRatio()));
        map.put("avgTireSlipAngle", corners(s.avgTireSlipAngle()));
        map.put("avgSuspensionTravelNormalized", corners(s.avgSuspensionTravelNormalized()));
        map.put("topSpeedMph", s.topSpeedMps() * 2.23694f);
        s.avgTireTempCelsius().ifPresent(temp -> map.put("avgTireTempCelsius", corners(temp)));
        s.peakPowerHp().ifPresent(hp -> map.put("peakPowerHp", hp));
        return map;
    }

    static Map<String, Object> tuningRecommendation(TuningRecommendation r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("style", r.style().name());
        map.put("tirePressurePsi", axlePair(r.tirePressurePsi()));
        map.put("camberDegrees", axlePair(r.camberDegrees()));
        map.put("toeDegrees", axlePair(r.toeDegrees()));
        map.put("frontCasterDegrees", r.frontCasterDegrees());
        map.put("rideHeightLevel", axlePair(r.rideHeightLevel()));
        map.put("aeroKgf", axlePair(r.aeroKgf()));
        map.put("brakeBalanceFrontPct", r.brakeBalanceFrontPct());
        map.put("brakePressurePct", r.brakePressurePct());
        map.put("diffAccelLockPct", r.diffAccelLockPct());
        map.put("diffDecelLockPct", r.diffDecelLockPct());
        r.rearDiffAccelLockPct().ifPresent(v -> map.put("rearDiffAccelLockPct", v));
        r.rearDiffDecelLockPct().ifPresent(v -> map.put("rearDiffDecelLockPct", v));
        r.centerDiffRearBiasPct().ifPresent(v -> map.put("centerDiffRearBiasPct", v));
        map.put("antiRollBarStiffness", axlePair(r.antiRollBarStiffness()));
        map.put("springRateNmm", axlePair(r.springRateNmm()));
        map.put("reboundDamping", axlePair(r.reboundDamping()));
        map.put("bumpDamping", axlePair(r.bumpDamping()));

        Map<String, Object> gearing = new LinkedHashMap<>();
        gearing.put("guidance", r.gearing().guidance());
        gearing.put("lean", r.gearing().lean());
        map.put("gearing", gearing);

        map.put("notes", r.notes());
        return map;
    }
}
