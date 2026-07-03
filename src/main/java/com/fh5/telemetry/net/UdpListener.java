package com.fh5.telemetry.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Binds a UDP socket on a background thread and hands each datagram to a
 * {@link PacketHandler}. Can be restarted with a different bind address or
 * port at any time, which is what lets the UI change the listen settings
 * without restarting the whole app.
 */
public final class UdpListener {

    private static final int RECEIVE_BUFFER_SIZE = 1500;

    private DatagramSocket socket;
    private Thread thread;
    private volatile String boundAddress;
    private volatile int boundPort;

    public synchronized void start(String bindAddress, int port, PacketHandler handler) throws IOException {
        stop();

        InetAddress address = (bindAddress == null || bindAddress.isBlank())
                ? null
                : InetAddress.getByName(bindAddress);
        socket = address == null ? new DatagramSocket(port) : new DatagramSocket(port, address);
        boundAddress = bindAddress;
        boundPort = port;

        DatagramSocket socketRef = socket;
        thread = new Thread(() -> runLoop(socketRef, handler), "udp-telemetry-listener");
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoop(DatagramSocket socket, PacketHandler handler) {
        byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handler.onPacket(packet.getData(), packet.getLength(), packet.getAddress().getHostAddress());
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.err.println("UDP listener error: " + e.getMessage());
                }
                return;
            }
        }
    }

    public synchronized void stop() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
        socket = null;
        thread = null;
    }

    public synchronized boolean isRunning() {
        return socket != null && !socket.isClosed();
    }

    public String boundAddress() {
        return boundAddress;
    }

    public int boundPort() {
        return boundPort;
    }
}
