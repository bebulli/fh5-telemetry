package com.fh5.telemetry.sample;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.fh5.telemetry.parser.PacketLayout.*;

/**
 * Builds raw Dash-format packet bytes with sensible defaults, for tests and
 * for running the parser/tuning engine without the game connected. Values
 * can be overridden individually; anything not overridden is a plausible
 * mid-corner driving snapshot.
 */
public final class SamplePacketBuilder {

    public static final int DASH_LENGTH = 324;

    private boolean isRaceOn = true;
    private long timestampMs = 10_000;
    private float engineMaxRpm = 7800f;
    private float engineIdleRpm = 900f;
    private float currentEngineRpm = 5200f;
    private float[] acceleration = {0.2f, -9.8f, 1.1f};
    private float[] tireSlipRatioFLFRRLRR = {0.05f, 0.06f, 0.03f, 0.03f};
    private float[] tireSlipAngleFLFRRLRR = {0.10f, 0.11f, 0.05f, 0.05f};
    private float[] suspensionTravelNormalizedFLFRRLRR = {0.4f, 0.4f, 0.35f, 0.35f};
    private int carOrdinal = 1;
    private int carClass = 5;
    private int carPerformanceIndex = 700;
    private int drivetrainType = 1;
    private int numCylinders = 6;
    private float speedMps = 40f;
    private float powerWatts = 220_000f;
    private float torqueNm = 450f;
    private float[] tireTempFLFRRLRR = {85f, 87f, 80f, 79f};
    private int gear = 3;

    public SamplePacketBuilder isRaceOn(boolean value) {
        this.isRaceOn = value;
        return this;
    }

    public SamplePacketBuilder currentEngineRpm(float value) {
        this.currentEngineRpm = value;
        return this;
    }

    public SamplePacketBuilder speedMps(float value) {
        this.speedMps = value;
        return this;
    }

    public SamplePacketBuilder gear(int value) {
        this.gear = value;
        return this;
    }

    public SamplePacketBuilder tireSlipRatio(float fl, float fr, float rl, float rr) {
        this.tireSlipRatioFLFRRLRR = new float[]{fl, fr, rl, rr};
        return this;
    }

    public SamplePacketBuilder tireSlipAngle(float fl, float fr, float rl, float rr) {
        this.tireSlipAngleFLFRRLRR = new float[]{fl, fr, rl, rr};
        return this;
    }

    public SamplePacketBuilder tireTemp(float fl, float fr, float rl, float rr) {
        this.tireTempFLFRRLRR = new float[]{fl, fr, rl, rr};
        return this;
    }

    public SamplePacketBuilder suspensionTravelNormalized(float fl, float fr, float rl, float rr) {
        this.suspensionTravelNormalizedFLFRRLRR = new float[]{fl, fr, rl, rr};
        return this;
    }

    public SamplePacketBuilder drivetrainType(int value) {
        this.drivetrainType = value;
        return this;
    }

    public SamplePacketBuilder carPerformanceIndex(int value) {
        this.carPerformanceIndex = value;
        return this;
    }

    public byte[] buildDash() {
        ByteBuffer buf = ByteBuffer.allocate(DASH_LENGTH).order(ByteOrder.LITTLE_ENDIAN);

        buf.putInt(IS_RACE_ON, isRaceOn ? 1 : 0);
        buf.putInt(TIMESTAMP_MS, (int) timestampMs);
        buf.putFloat(ENGINE_MAX_RPM, engineMaxRpm);
        buf.putFloat(ENGINE_IDLE_RPM, engineIdleRpm);
        buf.putFloat(CURRENT_ENGINE_RPM, currentEngineRpm);
        putVector3(buf, ACCELERATION, acceleration);
        putCorners(buf, SUSPENSION_TRAVEL_NORMALIZED, suspensionTravelNormalizedFLFRRLRR);
        putCorners(buf, TIRE_SLIP_RATIO, tireSlipRatioFLFRRLRR);
        putCorners(buf, TIRE_SLIP_ANGLE, tireSlipAngleFLFRRLRR);
        buf.putInt(CAR_ORDINAL, carOrdinal);
        buf.putInt(CAR_CLASS, carClass);
        buf.putInt(CAR_PERFORMANCE_INDEX, carPerformanceIndex);
        buf.putInt(DRIVETRAIN_TYPE, drivetrainType);
        buf.putInt(NUM_CYLINDERS, numCylinders);

        buf.putFloat(SPEED, speedMps);
        buf.putFloat(POWER, powerWatts);
        buf.putFloat(TORQUE, torqueNm);
        putCorners(buf, TIRE_TEMP, tireTempFLFRRLRR);
        buf.put(GEAR, (byte) gear);

        return buf.array();
    }

    private static void putVector3(ByteBuffer buf, int offset, float[] xyz) {
        buf.putFloat(offset, xyz[0]);
        buf.putFloat(offset + 4, xyz[1]);
        buf.putFloat(offset + 8, xyz[2]);
    }

    private static void putCorners(ByteBuffer buf, int offset, float[] flFrRlRr) {
        buf.putFloat(offset, flFrRlRr[0]);
        buf.putFloat(offset + 4, flFrRlRr[1]);
        buf.putFloat(offset + 8, flFrRlRr[2]);
        buf.putFloat(offset + 12, flFrRlRr[3]);
    }
}
