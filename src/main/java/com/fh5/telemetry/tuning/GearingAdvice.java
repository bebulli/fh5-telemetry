package com.fh5.telemetry.tuning;

/**
 * Forza doesn't expose a car's gear ratio table over telemetry, so this
 * stays descriptive rather than prescribing exact ratios.
 *
 * @param lean -1.0 (fully acceleration-focused/short) to +1.0 (fully top-speed-focused/tall)
 */
public record GearingAdvice(String guidance, float lean) {
}
