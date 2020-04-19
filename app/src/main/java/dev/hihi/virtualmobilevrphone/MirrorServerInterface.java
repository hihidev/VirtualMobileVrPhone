package dev.hihi.virtualmobilevrphone;

import java.net.InetSocketAddress;

public interface MirrorServerInterface {
    void start(final String ip, final int port, final Runnable connectedCallback,
            final Runnable stoppedCallback, final boolean receiveMode);
    void stop();
    void sendBuf(byte[] buf, int len);
    boolean isConnected();
    void waitUntilStopped();
    Packet getNextPacket();
    int packetQueueSize();
}
