package dev.hihi.virtualmobilevrphone;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class TcpServer implements MirrorServerInterface {

    private static final boolean DEBUG = true;

    private boolean isConnected = false;
    private boolean isRunning = false;

    private CountDownLatch mStoppingLock = new CountDownLatch(1);

    private Queue<Packet> mPendingPacketQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(final String debutTag, final InetSocketAddress address) {
        start(debutTag, address, null);
    }

    @Override
    public void start(final String debugTag, final InetSocketAddress address, final Runnable stoppedCallback) {
        isRunning = true;
        // Better way to handling threading?
        new Thread() {
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(address.getPort())) {
                    serverSocket.setReuseAddress(true);
                    while (isRunning) {
                        Log.i(debugTag, "Server is listening on port " + address.getPort());
                        final Socket socket = serverSocket.accept();
                        Log.i(debugTag, "Client connected");

                        // TODO: Identity verification !!!
                        // TODO: Encrypt the content !!!
                        mPendingPacketQueue.clear();
                        isConnected = true;

                        try (OutputStream os = socket.getOutputStream()) {
                            while (isRunning) {
                                // TODO: Better busy waiting?
                                if (mPendingPacketQueue.size() == 0) {
                                    if (socket.isClosed()) {
                                        break;
                                    }
                                    continue;
                                }
                                if (DEBUG) {
                                    Log.i(debugTag, "Ready to send, pending size: " + mPendingPacketQueue.size());
                                }
                                Packet packet = mPendingPacketQueue.poll();

                                // Header: Length of packet
                                os.write(((byte) (packet.size >> 24)) & 0xff);
                                os.write(((byte) (packet.size >> 16)) & 0xff);
                                os.write(((byte) (packet.size >> 8)) & 0xff);
                                os.write(((byte) (packet.size >> 0)) & 0xff);

                                // Payload
                                os.write(packet.bytes, 0, packet.size);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                socket.close();
                            } catch (IOException e) {
                            }
                        }
                        Log.i(debugTag, "Client disconnected");
                        isConnected = false;
                    }
                } catch (IOException ex) {
                    Log.e(debugTag, "Server exception: " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    mStoppingLock.countDown();
                    if (stoppedCallback != null) {
                        stoppedCallback.run();
                    }
                }
            }
        }.start();
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public void sendBuf(byte[] buf, int len) {
        // TODO: Better buf limit ?
        if (mPendingPacketQueue.size() >= 200) {
            Log.w("TcpServer", "Buffer full, mPendingPacketQueue size: " + mPendingPacketQueue.size());
            return;
        }
        // TODO: need extra copy?
        byte[] bytes = new byte[len];
        System.arraycopy(buf, 0, bytes, 0, len);
        mPendingPacketQueue.add(new Packet(bytes, len));
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public void waitUntilStopped() {
        try {
            synchronized (mStoppingLock) {
                mStoppingLock.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
