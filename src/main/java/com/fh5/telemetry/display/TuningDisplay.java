package com.fh5.telemetry.display;

import com.fh5.telemetry.tuning.AxlePair;
import com.fh5.telemetry.tuning.TuningRecommendation;

public final class TuningDisplay {

    public void print(TuningRecommendation r) {
        System.out.println("-- " + r.style() + " setup --");
        printAxle("Tire pressure (psi)", r.tirePressurePsi());
        printAxle("Camber (deg)", r.camberDegrees());
        printAxle("Toe (deg)", r.toeDegrees());
        System.out.printf("  %-22s %7.2f%n", "Front caster (deg)", r.frontCasterDegrees());
        printAxle("Ride height (mm)", r.rideHeightMm());
        printAxle("Aero level", r.aeroLevel());
        System.out.printf("  %-22s %7.2f%n", "Brake balance (%F)", r.brakeBalanceFrontPct());
        System.out.printf("  %-22s %7.2f%n", "Brake pressure (%)", r.brakePressurePct());
        System.out.printf("  %-22s %7.2f%n", "Diff accel lock (%)", r.diffAccelLockPct());
        System.out.printf("  %-22s %7.2f%n", "Diff decel lock (%)", r.diffDecelLockPct());
        printAxle("Anti-roll bar", r.antiRollBarStiffness());
        printAxle("Spring rate (N/mm)", r.springRateNmm());
        printAxle("Rebound damping", r.reboundDamping());
        printAxle("Bump damping", r.bumpDamping());
        System.out.printf("  %-22s %s%n", "Gearing", r.gearing().guidance());
        r.notes().forEach(note -> System.out.println("  * " + note));
    }

    private void printAxle(String label, AxlePair pair) {
        System.out.printf("  %-22s front=%7.2f  rear=%7.2f%n", label, pair.front(), pair.rear());
    }
}
