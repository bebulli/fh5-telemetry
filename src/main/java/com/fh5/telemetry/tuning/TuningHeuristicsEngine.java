package com.fh5.telemetry.tuning;

import com.fh5.telemetry.model.Corners;
import com.fh5.telemetry.model.DrivetrainType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a car spec and a driving sample into a starting tuning setup, using
 * heuristics that mirror what the Forza tuning community generally does by
 * hand: pick a baseline for the car's class and drivetrain, then nudge it
 * using whatever the tires and suspension are actually reporting.
 *
 * These are starting points for further fine-tuning on track, not a
 * physics solver. Forza doesn't expose its internal tuning math, so most
 * of the scales used here (ARB 1-65, dampers ~1-30, aero as a 0-100 level)
 * match the ranges the in-game sliders use, not derived physical units.
 * Spring rate is the exception: its N/mm range (528.3 to 2641.3 for a
 * race-modified car) is a real reported in-game bound, not a guess.
 */
public final class TuningHeuristicsEngine {

    private static final float RACE_PRESSURE_PSI = 26f;
    private static final float STREET_PRESSURE_PSI = 34f;
    private static final float IDEAL_TIRE_TEMP_LOW_C = 79f;
    private static final float IDEAL_TIRE_TEMP_HIGH_C = 93f;
    private static final float PRESSURE_PER_DEGREE_OFF_TARGET = 0.05f;
    private static final float DRIFT_FRONT_PRESSURE_DELTA = -1.5f;
    private static final float DRIFT_REAR_PRESSURE_DELTA = 1.5f;
    private static final float MIN_PRESSURE_PSI = 22f;
    private static final float MAX_PRESSURE_PSI = 45f;

    private static final float STREET_FRONT_CAMBER = -1.5f;
    private static final float RACE_FRONT_CAMBER = -3.0f;
    private static final float STREET_REAR_CAMBER = -1.0f;
    private static final float RACE_REAR_CAMBER = -2.5f;
    private static final float SLIP_ANGLE_CAMBER_GAIN = 4f;
    private static final float MAX_CAMBER_ADJUST = 1.0f;
    private static final float BASE_FRONT_TOE = -0.1f;
    private static final float BASE_REAR_TOE = 0.15f;
    private static final float DRIFT_FRONT_CAMBER_DELTA = -1.0f;
    private static final float DRIFT_REAR_CAMBER_DELTA = 1.2f;
    private static final float DRIFT_FRONT_TOE_DELTA = -0.3f;
    private static final float DRIFT_REAR_TOE_DELTA = -0.15f;

    private static final float STREET_CASTER_DEGREES = 3.0f;
    private static final float RACE_CASTER_DEGREES = 6.5f;
    private static final float MIN_CASTER_DEGREES = 1.0f;
    private static final float MAX_CASTER_DEGREES = 7.0f;
    private static final float SYMPTOM_UNDERSTEER_CASTER_DELTA = 0.5f;

    private static final float STREET_RIDE_HEIGHT_MM = 150f;
    private static final float RACE_RIDE_HEIGHT_MM = 110f;
    private static final float RACE_RIDE_HEIGHT_RAKE_MM = 5f;
    private static final float MIN_RIDE_HEIGHT_MM = 80f;
    private static final float MAX_RIDE_HEIGHT_MM = 200f;
    private static final float BOTTOMING_RIDE_HEIGHT_RAISE_MM = 6f;

    private static final float STREET_AERO_LEVEL = 15f;
    private static final float RACE_AERO_LEVEL = 70f;
    private static final float AERO_FRONT_TO_REAR_RATIO = 0.8f;
    private static final float MIN_AERO_LEVEL = 0f;
    private static final float MAX_AERO_LEVEL = 100f;
    private static final float DRIFT_AERO_MULTIPLIER = 0.3f;

    private static final float BASE_BRAKE_BALANCE_FRONT_PCT = 52f;
    private static final float MIN_BRAKE_BALANCE_FRONT_PCT = 25f;
    private static final float MAX_BRAKE_BALANCE_FRONT_PCT = 75f;
    private static final float STREET_BRAKE_PRESSURE_PCT = 100f;
    private static final float RACE_BRAKE_PRESSURE_PCT = 150f;
    private static final float MIN_BRAKE_PRESSURE_PCT = 50f;
    private static final float MAX_BRAKE_PRESSURE_PCT = 200f;

    private static final float MIN_DIFF_LOCK_PCT = 0f;
    private static final float MAX_DIFF_LOCK_PCT = 100f;
    private static final float DRIFT_ACCEL_LOCK_DELTA = 20f;
    private static final float DRIFT_DECEL_LOCK_DELTA = 15f;
    private static final float SYMPTOM_TRACTION_LOSS_ACCEL_LOCK_DELTA = 10f;

    private static final float ARB_MIN = 1f;
    private static final float ARB_MAX = 65f;
    private static final float ARB_BASE_PER_TONNE = 18f;
    private static final float ARB_PI_GAIN_PER_TONNE = 12f;
    private static final float SLIP_ANGLE_ARB_GAIN = 30f;
    private static final float DRIFT_FRONT_ARB_DELTA = -6f;
    private static final float DRIFT_REAR_ARB_DELTA = 8f;

    // Real in-game bounds for a race-modified car's spring rate slider.
    private static final float SPRING_RATE_MIN_NMM = 528.3f;
    private static final float SPRING_RATE_MAX_NMM = 2641.3f;
    private static final float SPRING_RATE_PER_TONNE_STREET_NMM = 650f;
    private static final float SPRING_RATE_PER_TONNE_RACE_NMM = 1450f;
    private static final float SUSPENSION_BOTTOMING_THRESHOLD = 0.85f;
    private static final float BOTTOMING_SPRING_RATE_BOOST = 1.15f;
    private static final float DRIFT_FRONT_SPRING_MULTIPLIER = 1.05f;
    private static final float DRIFT_REAR_SPRING_MULTIPLIER = 0.9f;

    private static final float DAMPER_REBOUND_FROM_SPRING_RATE = 0.015f;
    private static final float DAMPER_BUMP_TO_REBOUND_RATIO = 0.6f;

    private static final float SYMPTOM_CAMBER_DELTA = 0.6f;
    private static final float SYMPTOM_ARB_DELTA = 5f;
    private static final float SYMPTOM_TRACTION_LOSS_REAR_PRESSURE_DELTA = -1.5f;
    private static final float SYMPTOM_TRACTION_LOSS_REAR_ARB_DELTA = -4f;
    private static final float SYMPTOM_TRACTION_LOSS_REAR_SPRING_MULTIPLIER = 0.93f;
    private static final float SYMPTOM_BOUNCY_SPRING_MULTIPLIER = 1.2f;

    public TuningRecommendation recommend(CarSpec spec, TelemetrySampleSummary summary, TuningStyle style) {
        return recommend(spec, summary, style, Set.of());
    }

    public TuningRecommendation recommend(
            CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, Set<DrivingSymptom> symptoms) {
        List<String> notes = new ArrayList<>();

        AxlePair pressure = computeTirePressure(spec, summary, style, symptoms, notes);
        GearingAdvice gearing = computeGearing(spec, summary, style, notes);
        AxlePair camber = computeCamber(spec, summary, style, symptoms, notes);
        AxlePair toe = computeToe(style);
        float caster = computeCaster(spec, symptoms);
        AxlePair rideHeight = computeRideHeight(spec, summary, style, notes);
        AxlePair aero = computeAero(spec, style);
        float brakeBalance = computeBrakeBalance(spec);
        float brakePressure = computeBrakePressure(spec);
        float accelLock = computeAccelDiffLock(spec, style, symptoms, notes);
        float decelLock = computeDecelDiffLock(spec, style);
        AxlePair arb = computeArbStiffness(spec, summary, style, symptoms, notes);
        AxlePair springRate = computeSpringRate(spec, summary, style, symptoms, notes);
        AxlePair rebound = computeDamper(springRate, DAMPER_REBOUND_FROM_SPRING_RATE);
        AxlePair bump = computeDamper(rebound, DAMPER_BUMP_TO_REBOUND_RATIO);

        return new TuningRecommendation(
                style, pressure, gearing, camber, toe, caster, rideHeight, aero,
                brakeBalance, brakePressure, accelLock, decelLock,
                arb, springRate, rebound, bump, notes);
    }

    private AxlePair computeTirePressure(
            CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, Set<DrivingSymptom> symptoms, List<String> notes) {
        float base = lerpByPerformanceIndex(spec.performanceIndex(), STREET_PRESSURE_PSI, RACE_PRESSURE_PSI);

        float front = base;
        float rear = base;

        if (summary.avgTireTempCelsius().isPresent()) {
            Corners temp = summary.avgTireTempCelsius().get();
            front += pressureAdjustmentForTemp(temp.frontAverage());
            rear += pressureAdjustmentForTemp(temp.rearAverage());
            if (temp.frontAverage() > IDEAL_TIRE_TEMP_HIGH_C || temp.rearAverage() > IDEAL_TIRE_TEMP_HIGH_C) {
                notes.add("Tires are running hot versus the ideal 79-93C window; pressure lowered on the hot end(s) to grow the contact patch.");
            }
        }

        if (style == TuningStyle.DRIFT) {
            front += DRIFT_FRONT_PRESSURE_DELTA;
            rear += DRIFT_REAR_PRESSURE_DELTA;
            notes.add("Drift setup: lower front pressure for turn-in bite, higher rear pressure to reduce rear grip and make the slide easier to hold.");
        }

        if (symptoms.contains(DrivingSymptom.TRACTION_LOSS)) {
            rear += SYMPTOM_TRACTION_LOSS_REAR_PRESSURE_DELTA;
            notes.add("Reported traction loss: lowered rear pressure to grow the rear contact patch.");
        }

        return new AxlePair(clamp(front, MIN_PRESSURE_PSI, MAX_PRESSURE_PSI), clamp(rear, MIN_PRESSURE_PSI, MAX_PRESSURE_PSI));
    }

    private float pressureAdjustmentForTemp(float avgTempC) {
        if (avgTempC > IDEAL_TIRE_TEMP_HIGH_C) {
            return -(avgTempC - IDEAL_TIRE_TEMP_HIGH_C) * PRESSURE_PER_DEGREE_OFF_TARGET;
        }
        if (avgTempC < IDEAL_TIRE_TEMP_LOW_C) {
            return (IDEAL_TIRE_TEMP_LOW_C - avgTempC) * PRESSURE_PER_DEGREE_OFF_TARGET;
        }
        return 0f;
    }

    private GearingAdvice computeGearing(CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, List<String> notes) {
        float powerToWeight = spec.powerToWeightHpPerTonne();
        float lean = clamp((powerToWeight - 150f) / 250f, -1f, 1f);

        if (style == TuningStyle.DRIFT) {
            lean = -0.6f;
            notes.add("Drift setup: gearing pulled shorter so the engine stays in its torque band mid-slide instead of chasing top speed.");
            return new GearingAdvice(
                    "Favor a shorter final drive; aim to be near peak torque through second and third gear rather than stretching for top speed.",
                    lean);
        }

        double topSpeedMph = summary.topSpeedMps() * 2.23694;
        String guidance = lean >= 0
                ? String.format("Power-to-weight suggests taller gearing; set the final drive so top gear tops out a bit above the %.0f mph you've reached so far.", topSpeedMph)
                : String.format("Power-to-weight suggests shorter gearing for stronger acceleration out of corners; observed top speed so far is %.0f mph.", topSpeedMph);

        return new GearingAdvice(guidance, lean);
    }

    private AxlePair computeCamber(
            CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, Set<DrivingSymptom> symptoms, List<String> notes) {
        float front = lerpByPerformanceIndex(spec.performanceIndex(), STREET_FRONT_CAMBER, RACE_FRONT_CAMBER);
        float rear = lerpByPerformanceIndex(spec.performanceIndex(), STREET_REAR_CAMBER, RACE_REAR_CAMBER);

        float slipAngleImbalance = summary.avgTireSlipAngle().frontAverage() - summary.avgTireSlipAngle().rearAverage();
        float adjust = clamp(slipAngleImbalance * SLIP_ANGLE_CAMBER_GAIN, -MAX_CAMBER_ADJUST, MAX_CAMBER_ADJUST);
        if (Math.abs(adjust) > 0.2f) {
            notes.add(adjust > 0
                    ? "Front slip angle running higher than rear (understeer signature); added front camber for more front lateral grip."
                    : "Rear slip angle running higher than front (oversteer signature); added rear camber for more rear lateral grip.");
        }
        front -= adjust;
        rear -= adjust * 0.5f;

        if (style == TuningStyle.DRIFT) {
            front += DRIFT_FRONT_CAMBER_DELTA;
            rear += DRIFT_REAR_CAMBER_DELTA;
            notes.add("Drift setup: extra front camber keeps front bite while sideways; rear camber pulled back so the rear breaks loose predictably.");
        }

        if (symptoms.contains(DrivingSymptom.UNDERSTEER)) {
            front -= SYMPTOM_CAMBER_DELTA;
            notes.add("Reported understeer: added front camber for more front lateral grip.");
        }
        if (symptoms.contains(DrivingSymptom.OVERSTEER)) {
            rear -= SYMPTOM_CAMBER_DELTA;
            notes.add("Reported oversteer: added rear camber for more rear lateral grip.");
        }

        return new AxlePair(front, Math.min(rear, -0.1f));
    }

    private AxlePair computeToe(TuningStyle style) {
        float front = BASE_FRONT_TOE;
        float rear = BASE_REAR_TOE;
        if (style == TuningStyle.DRIFT) {
            front += DRIFT_FRONT_TOE_DELTA;
            rear += DRIFT_REAR_TOE_DELTA;
        }
        return new AxlePair(front, rear);
    }

    private float computeCaster(CarSpec spec, Set<DrivingSymptom> symptoms) {
        float caster = lerpByPerformanceIndex(spec.performanceIndex(), STREET_CASTER_DEGREES, RACE_CASTER_DEGREES);
        if (symptoms.contains(DrivingSymptom.UNDERSTEER)) {
            // More caster increases camber gain while turning, adding front grip mid-corner.
            caster += SYMPTOM_UNDERSTEER_CASTER_DELTA;
        }
        return clamp(caster, MIN_CASTER_DEGREES, MAX_CASTER_DEGREES);
    }

    private AxlePair computeRideHeight(CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, List<String> notes) {
        float base = lerpByPerformanceIndex(spec.performanceIndex(), STREET_RIDE_HEIGHT_MM, RACE_RIDE_HEIGHT_MM);
        float front = base;
        float rear = base;

        if (style != TuningStyle.DRIFT) {
            // Slight rake (front lower than rear) helps front grip and airflow to the rear.
            front -= RACE_RIDE_HEIGHT_RAKE_MM;
        }

        Corners travel = summary.avgSuspensionTravelNormalized();
        if (travel.frontAverage() > SUSPENSION_BOTTOMING_THRESHOLD) {
            front += BOTTOMING_RIDE_HEIGHT_RAISE_MM;
            notes.add("Front suspension travel is near its limit; raised front ride height a bit as well as stiffening the spring.");
        }
        if (travel.rearAverage() > SUSPENSION_BOTTOMING_THRESHOLD) {
            rear += BOTTOMING_RIDE_HEIGHT_RAISE_MM;
            notes.add("Rear suspension travel is near its limit; raised rear ride height a bit as well as stiffening the spring.");
        }

        return new AxlePair(clamp(front, MIN_RIDE_HEIGHT_MM, MAX_RIDE_HEIGHT_MM), clamp(rear, MIN_RIDE_HEIGHT_MM, MAX_RIDE_HEIGHT_MM));
    }

    private AxlePair computeAero(CarSpec spec, TuningStyle style) {
        float overall = lerpByPerformanceIndex(spec.performanceIndex(), STREET_AERO_LEVEL, RACE_AERO_LEVEL);
        float front = overall * AERO_FRONT_TO_REAR_RATIO;
        float rear = overall;

        if (style == TuningStyle.DRIFT) {
            // Less aero, especially at the rear, keeps the car rotatable instead of planted.
            front *= DRIFT_AERO_MULTIPLIER;
            rear *= DRIFT_AERO_MULTIPLIER;
        }

        return new AxlePair(clamp(front, MIN_AERO_LEVEL, MAX_AERO_LEVEL), clamp(rear, MIN_AERO_LEVEL, MAX_AERO_LEVEL));
    }

    private float computeBrakeBalance(CarSpec spec) {
        float balance = BASE_BRAKE_BALANCE_FRONT_PCT;
        // Cars with more weight over the front axle need more front brake bias to match the load.
        balance += (spec.frontWeightDistributionPct() - 50f) * 0.3f;
        return clamp(balance, MIN_BRAKE_BALANCE_FRONT_PCT, MAX_BRAKE_BALANCE_FRONT_PCT);
    }

    private float computeBrakePressure(CarSpec spec) {
        float pressure = lerpByPerformanceIndex(spec.performanceIndex(), STREET_BRAKE_PRESSURE_PCT, RACE_BRAKE_PRESSURE_PCT);
        return clamp(pressure, MIN_BRAKE_PRESSURE_PCT, MAX_BRAKE_PRESSURE_PCT);
    }

    private float computeAccelDiffLock(CarSpec spec, TuningStyle style, Set<DrivingSymptom> symptoms, List<String> notes) {
        float lock = switch (spec.drivetrain()) {
            case FWD -> 15f;
            case RWD -> 25f;
            case AWD, UNKNOWN -> 30f;
        };

        if (style == TuningStyle.DRIFT) {
            lock += DRIFT_ACCEL_LOCK_DELTA;
        }
        if (symptoms.contains(DrivingSymptom.TRACTION_LOSS)) {
            lock += SYMPTOM_TRACTION_LOSS_ACCEL_LOCK_DELTA;
            notes.add("Reported traction loss: increased acceleration differential lock so power gets split more evenly across the axle.");
        }

        return clamp(lock, MIN_DIFF_LOCK_PCT, MAX_DIFF_LOCK_PCT);
    }

    private float computeDecelDiffLock(CarSpec spec, TuningStyle style) {
        float lock = switch (spec.drivetrain()) {
            case FWD -> 5f;
            case RWD -> 10f;
            case AWD, UNKNOWN -> 12f;
        };

        if (style == TuningStyle.DRIFT) {
            lock += DRIFT_DECEL_LOCK_DELTA;
        }

        return clamp(lock, MIN_DIFF_LOCK_PCT, MAX_DIFF_LOCK_PCT);
    }

    private AxlePair computeArbStiffness(
            CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, Set<DrivingSymptom> symptoms, List<String> notes) {
        float tonnes = spec.weightKg() / 1000f;
        float overallStiffness = ARB_BASE_PER_TONNE * tonnes
                + ARB_PI_GAIN_PER_TONNE * tonnes * (spec.performanceIndex() / 999f);

        float frontShare = drivetrainFrontArbShare(spec.drivetrain());
        float front = overallStiffness * frontShare;
        float rear = overallStiffness * (1 - frontShare);

        float slipAngleImbalance = summary.avgTireSlipAngle().frontAverage() - summary.avgTireSlipAngle().rearAverage();
        float adjust = clamp(slipAngleImbalance * SLIP_ANGLE_ARB_GAIN, -10f, 10f);
        front -= adjust;
        rear += adjust;

        if (style == TuningStyle.DRIFT) {
            front += DRIFT_FRONT_ARB_DELTA;
            rear += DRIFT_REAR_ARB_DELTA;
            notes.add("Drift setup: stiffened rear bar and softened front bar to shift mechanical grip toward the front axle.");
        }

        if (symptoms.contains(DrivingSymptom.UNDERSTEER)) {
            front -= SYMPTOM_ARB_DELTA;
            rear += SYMPTOM_ARB_DELTA;
            notes.add("Reported understeer: softened front bar and stiffened rear bar to rotate the car more.");
        }
        if (symptoms.contains(DrivingSymptom.OVERSTEER)) {
            front += SYMPTOM_ARB_DELTA;
            rear -= SYMPTOM_ARB_DELTA;
            notes.add("Reported oversteer: stiffened front bar and softened rear bar for more stability.");
        }
        if (symptoms.contains(DrivingSymptom.TRACTION_LOSS)) {
            rear += SYMPTOM_TRACTION_LOSS_REAR_ARB_DELTA;
            notes.add("Reported traction loss: softened rear bar so both rear tires keep more even contact with the road.");
        }

        return new AxlePair(clamp(front, ARB_MIN, ARB_MAX), clamp(rear, ARB_MIN, ARB_MAX));
    }

    private float drivetrainFrontArbShare(DrivetrainType drivetrain) {
        return switch (drivetrain) {
            case FWD -> 0.42f;
            case RWD -> 0.58f;
            case AWD, UNKNOWN -> 0.5f;
        };
    }

    private AxlePair computeSpringRate(
            CarSpec spec, TelemetrySampleSummary summary, TuningStyle style, Set<DrivingSymptom> symptoms, List<String> notes) {
        float ratePerTonne = lerpByPerformanceIndex(spec.performanceIndex(), SPRING_RATE_PER_TONNE_STREET_NMM, SPRING_RATE_PER_TONNE_RACE_NMM);
        float frontTonnes = spec.weightKg() / 1000f * (spec.frontWeightDistributionPct() / 100f);
        float rearTonnes = spec.weightKg() / 1000f * (1 - spec.frontWeightDistributionPct() / 100f);

        float front = ratePerTonne * frontTonnes;
        float rear = ratePerTonne * rearTonnes;

        Corners travel = summary.avgSuspensionTravelNormalized();
        if (travel.frontAverage() > SUSPENSION_BOTTOMING_THRESHOLD) {
            front *= BOTTOMING_SPRING_RATE_BOOST;
            notes.add("Front suspension travel is near its limit; stiffened front springs to reduce bottoming out.");
        }
        if (travel.rearAverage() > SUSPENSION_BOTTOMING_THRESHOLD) {
            rear *= BOTTOMING_SPRING_RATE_BOOST;
            notes.add("Rear suspension travel is near its limit; stiffened rear springs to reduce bottoming out.");
        }

        if (style == TuningStyle.DRIFT) {
            front *= DRIFT_FRONT_SPRING_MULTIPLIER;
            rear *= DRIFT_REAR_SPRING_MULTIPLIER;
        }

        if (symptoms.contains(DrivingSymptom.TRACTION_LOSS)) {
            rear *= SYMPTOM_TRACTION_LOSS_REAR_SPRING_MULTIPLIER;
            notes.add("Reported traction loss: softened rear springs to keep a more consistent contact patch under power.");
        }
        if (symptoms.contains(DrivingSymptom.BOUNCY_SUSPENSION)) {
            front *= SYMPTOM_BOUNCY_SPRING_MULTIPLIER;
            rear *= SYMPTOM_BOUNCY_SPRING_MULTIPLIER;
            notes.add("Reported bouncy/bottoming-out suspension: stiffened springs on both axles.");
        }

        return new AxlePair(
                clamp(front, SPRING_RATE_MIN_NMM, SPRING_RATE_MAX_NMM),
                clamp(rear, SPRING_RATE_MIN_NMM, SPRING_RATE_MAX_NMM));
    }

    private AxlePair computeDamper(AxlePair reference, float factor) {
        return new AxlePair(reference.front() * factor, reference.rear() * factor);
    }

    private static float lerpByPerformanceIndex(int performanceIndex, float atLowPi, float atHighPi) {
        float t = clamp((performanceIndex - 100) / 899f, 0f, 1f);
        return atLowPi + (atHighPi - atLowPi) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
