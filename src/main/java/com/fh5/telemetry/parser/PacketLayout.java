package com.fh5.telemetry.parser;

/**
 * Byte offsets for the Forza "Data Out" packet, shared by the parser (decode)
 * and the sample packet builder (encode, used by tests and offline demos) so
 * there's exactly one place that knows the wire layout.
 *
 * The Sled fields (0-231) have been stable since FM6/FM7 and are a strict
 * prefix of every later format. FH5's Dash packet appends dashboard fields
 * after byte 232, then 13 bytes of undocumented trailing data to reach 324.
 */
public final class PacketLayout {

    private PacketLayout() {
    }

    public static final int IS_RACE_ON = 0;
    public static final int TIMESTAMP_MS = 4;
    public static final int ENGINE_MAX_RPM = 8;
    public static final int ENGINE_IDLE_RPM = 12;
    public static final int CURRENT_ENGINE_RPM = 16;
    public static final int ACCELERATION = 20;
    public static final int VELOCITY = 32;
    public static final int ANGULAR_VELOCITY = 44;
    public static final int YAW = 56;
    public static final int PITCH = 60;
    public static final int ROLL = 64;
    public static final int SUSPENSION_TRAVEL_NORMALIZED = 68;
    public static final int TIRE_SLIP_RATIO = 84;
    public static final int WHEEL_ROTATION_SPEED = 100;
    // 116: WheelOnRumbleStrip, 132: WheelInPuddleDepth, 148: SurfaceRumble - not currently modeled.
    public static final int TIRE_SLIP_ANGLE = 164;
    public static final int TIRE_COMBINED_SLIP = 180;
    public static final int SUSPENSION_TRAVEL_METERS = 196;
    public static final int CAR_ORDINAL = 212;
    public static final int CAR_CLASS = 216;
    public static final int CAR_PERFORMANCE_INDEX = 220;
    public static final int DRIVETRAIN_TYPE = 224;
    public static final int NUM_CYLINDERS = 228;

    public static final int POSITION = 232;
    public static final int SPEED = 244;
    public static final int POWER = 248;
    public static final int TORQUE = 252;
    public static final int TIRE_TEMP = 256;
    public static final int BOOST = 272;
    public static final int FUEL = 276;
    public static final int DISTANCE_TRAVELED = 280;
    public static final int BEST_LAP = 284;
    public static final int LAST_LAP = 288;
    public static final int CURRENT_LAP = 292;
    public static final int CURRENT_RACE_TIME = 296;
    public static final int LAP = 300;
    public static final int RACE_POSITION = 302;
    public static final int ACCEL = 303;
    public static final int BRAKE = 304;
    public static final int CLUTCH = 305;
    public static final int HANDBRAKE = 306;
    public static final int GEAR = 307;
    public static final int STEER = 308;
    public static final int NORMALIZED_DRIVING_LINE = 309;
    public static final int NORMALIZED_AI_BRAKE_DIFFERENCE = 310;
}
