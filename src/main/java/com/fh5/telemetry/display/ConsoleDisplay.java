package com.fh5.telemetry.display;

import com.fh5.telemetry.model.Corners;
import com.fh5.telemetry.model.TelemetryPacket;

public final class ConsoleDisplay {

    public void print(TelemetryPacket t) {
        System.out.printf(
                "[%s] speed=%5.1f mph  rpm=%5.0f/%5.0f  gear=%s  %s%n",
                t.drivingState(),
                t.speedMps() * 2.23694,
                t.currentEngineRpm(),
                t.engineMaxRpm(),
                t.dash().map(d -> String.valueOf(d.gear())).orElse("-"),
                t.isDash() ? formatDash(t) : "");

        System.out.printf(
                "  slip ratio     FL=%6.2f FR=%6.2f RL=%6.2f RR=%6.2f%n",
                t.tireSlipRatio().frontLeft(), t.tireSlipRatio().frontRight(),
                t.tireSlipRatio().rearLeft(), t.tireSlipRatio().rearRight());

        t.dash().ifPresent(dash -> {
            Corners temp = dash.tireTempCelsius();
            System.out.printf(
                    "  tire temp (C)  FL=%6.1f FR=%6.1f RL=%6.1f RR=%6.1f%n",
                    temp.frontLeft(), temp.frontRight(), temp.rearLeft(), temp.rearRight());
        });

        Corners susp = t.suspensionTravelNormalized();
        System.out.printf(
                "  susp travel    FL=%6.2f FR=%6.2f RL=%6.2f RR=%6.2f%n",
                susp.frontLeft(), susp.frontRight(), susp.rearLeft(), susp.rearRight());

        System.out.printf(
                "  accel (g)      x=%5.2f y=%5.2f z=%5.2f%n",
                t.acceleration().x() / 9.81f, t.acceleration().y() / 9.81f, t.acceleration().z() / 9.81f);
    }

    private String formatDash(TelemetryPacket t) {
        return t.dash()
                .map(d -> String.format("power=%.0fhp torque=%.0fNm fuel=%.0f%%",
                        d.powerWatts() / 745.7f, d.torqueNm(), d.fuel() * 100))
                .orElse("");
    }
}
