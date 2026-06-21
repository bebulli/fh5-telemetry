package com.fh5.telemetry.display;

import com.fh5.telemetry.tuning.AxlePair;
import com.fh5.telemetry.tuning.TuningRecommendation;

public final class TuningDisplay {

    public void print(TuningRecommendation r) {
        System.out.println("-- " + r.style() + " setup --");
        printAxle("Tire pressure (psi)", r.tirePressurePsi());
        printAxle("Camber (deg)", r.camberDegrees());
        printAxle("Toe (deg)", r.toeDegrees());
        printAxle("Anti-roll bar", r.antiRollBarStiffness());
        printAxle("Spring rate (lbs/in)", r.springRateLbsPerIn());
        printAxle("Rebound damping", r.reboundDamping());
        printAxle("Bump damping", r.bumpDamping());
        System.out.printf("  %-22s %s%n", "Gearing", r.gearing().guidance());
        r.notes().forEach(note -> System.out.println("  * " + note));
    }

    private void printAxle(String label, AxlePair pair) {
        System.out.printf("  %-22s front=%7.2f  rear=%7.2f%n", label, pair.front(), pair.rear());
    }
}
