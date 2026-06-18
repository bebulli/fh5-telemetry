package com.fh5.telemetry.sniff;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Standalone sanity check: binds to the Forza "Data Out" port and prints
 * whatever arrives, with no parsing. Run this first to confirm the PS5 is
 * actually reaching this PC before touching the real parser.
 */
public final class RawPacketSniffer {

    private static final int PORT = 6767;
    private static final int MAX_PACKET_SIZE = 1500;
    private static final int PREVIEW_BYTES = 16;

    public static void main(String[] args) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("Listening for FH5 Data Out packets on UDP " + PORT + "...");
            System.out.println("Enable Data Out on the PS5 and point it at this PC's IP.");

            byte[] buffer = new byte[MAX_PACKET_SIZE];
            long count = 0;

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                count++;

                System.out.printf(
                        "[%d] %d bytes from %s: %s%n",
                        count,
                        packet.getLength(),
                        packet.getAddress().getHostAddress(),
                        toHex(packet.getData(), packet.getLength()));
            }
        }
    }

    private static String toHex(byte[] data, int length) {
        int previewLength = Math.min(length, PREVIEW_BYTES);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < previewLength; i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        if (length > previewLength) {
            sb.append("...");
        }
        return sb.toString().trim();
    }
}
