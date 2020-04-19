package dev.hihi.virtualmobilevrphone;

import java.net.InetSocketAddress;

public interface MirrorServerInterface {
    void start(String ip, int port, Runnable stoppedCallback, boolean receiveMode);
    void stop();
    void sendBuf(byte[] buf, int len);
    boolean isConnected();
    void waitUntilStopped();
    Packet getNextPacket();
    int packetQueueSize();
}
