package dev.hihi.virtualmobilevrphone;

import java.net.InetSocketAddress;

public interface MirrorServerInterface {
    void start(String debugTag, InetSocketAddress address);
    void start(String debugTag, InetSocketAddress address, Runnable stoppedCallback, boolean receiveMode);
    void stop();
    void sendBuf(byte[] buf, int len);
    boolean isConnected();
    void waitUntilStopped();
    Packet getNextPacket();
    int packetQueueSize();
}
