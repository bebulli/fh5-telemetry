package com.fh5.telemetry.parser;

import com.fh5.telemetry.model.Corners;
import com.fh5.telemetry.model.DashData;
import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.model.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import static com.fh5.telemetry.parser.PacketLayout.*;

/**
 * Decodes raw Forza "Data Out" UDP payloads into {@link TelemetryPacket}.
 * See {@link PacketLayout} for the field offsets this relies on.
 */
public final class TelemetryParser {

    public TelemetryPacket parse(byte[] data, int length) {
        PacketFormat format = PacketFormat.fromLength(length);
        ByteBuffer buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN);

        boolean isRaceOn = buf.getInt(IS_RACE_ON) != 0;
        long timestampMs = Integer.toUnsignedLong(buf.getInt(TIMESTAMP_MS));

        Optional<DashData> dash = format == PacketFormat.DASH
                ? Optional.of(parseDash(buf))
                : Optional.empty();

        return new TelemetryPacket(
                isRaceOn,
                timestampMs,
                buf.getFloat(ENGINE_MAX_RPM),
                buf.getFloat(ENGINE_IDLE_RPM),
                buf.getFloat(CURRENT_ENGINE_RPM),
                readVector3(buf, ACCELERATION),
                readVector3(buf, VELOCITY),
                readVector3(buf, ANGULAR_VELOCITY),
                buf.getFloat(YAW),
                buf.getFloat(PITCH),
                buf.getFloat(ROLL),
                readCorners(buf, SUSPENSION_TRAVEL_NORMALIZED),
                readCorners(buf, TIRE_SLIP_RATIO),
                readCorners(buf, WHEEL_ROTATION_SPEED),
                readCorners(buf, TIRE_SLIP_ANGLE),
                readCorners(buf, TIRE_COMBINED_SLIP),
                readCorners(buf, SUSPENSION_TRAVEL_METERS),
                buf.getInt(CAR_ORDINAL),
                buf.getInt(CAR_CLASS),
                buf.getInt(CAR_PERFORMANCE_INDEX),
                DrivetrainType.fromWireValue(buf.getInt(DRIVETRAIN_TYPE)),
                buf.getInt(NUM_CYLINDERS),
                dash);
    }

    private DashData parseDash(ByteBuffer buf) {
        return new DashData(
                readVector3(buf, POSITION),
                buf.getFloat(SPEED),
                buf.getFloat(POWER),
                buf.getFloat(TORQUE),
                readCorners(buf, TIRE_TEMP),
                buf.getFloat(BOOST),
                buf.getFloat(FUEL),
                buf.getFloat(DISTANCE_TRAVELED),
                buf.getFloat(BEST_LAP),
                buf.getFloat(LAST_LAP),
                buf.getFloat(CURRENT_LAP),
                buf.getFloat(CURRENT_RACE_TIME),
                Short.toUnsignedInt(buf.getShort(LAP)),
                Byte.toUnsignedInt(buf.get(RACE_POSITION)),
                Byte.toUnsignedInt(buf.get(ACCEL)),
                Byte.toUnsignedInt(buf.get(BRAKE)),
                Byte.toUnsignedInt(buf.get(CLUTCH)),
                Byte.toUnsignedInt(buf.get(HANDBRAKE)),
                Byte.toUnsignedInt(buf.get(GEAR)),
                buf.get(STEER),
                buf.get(NORMALIZED_DRIVING_LINE),
                buf.get(NORMALIZED_AI_BRAKE_DIFFERENCE));
    }

    private static Vector3 readVector3(ByteBuffer buf, int offset) {
        return new Vector3(buf.getFloat(offset), buf.getFloat(offset + 4), buf.getFloat(offset + 8));
    }

    private static Corners readCorners(ByteBuffer buf, int offset) {
        return new Corners(
                buf.getFloat(offset),
                buf.getFloat(offset + 4),
                buf.getFloat(offset + 8),
                buf.getFloat(offset + 12));
    }
}
