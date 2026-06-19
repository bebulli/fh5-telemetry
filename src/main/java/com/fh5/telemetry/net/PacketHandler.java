package com.fh5.telemetry.net;

@FunctionalInterface
public interface PacketHandler {
    void onPacket(byte[] data, int length, String senderAddress);
}
