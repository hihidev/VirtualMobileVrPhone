package dev.hihi.virtualmobilevrphone;

import java.net.InetSocketAddress;

public interface MirrorServerInterface {
    void start(InetSocketAddress address);
    void stop();
    void sendBuf(byte[] buf, int len);
    boolean isConnected();
    void waitUntilStopped();
}
