package dev.hihi.virtualmobilevrphone;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class Tcp implements MirrorServerInterface {

    private static final boolean DEBUG = true;

    volatile private boolean mIsConnected = false;
    volatile private boolean mIsRunning = false;
    volatile  ServerSocket mServerSocket;

    private final boolean mIsServer;
    private final String mTag;

    private CountDownLatch mStoppingLock = new CountDownLatch(1);

    private Queue<Packet> mPendingPacketQueue = new ConcurrentLinkedQueue<>();

    public Tcp(String tag, boolean isServer) {
        mTag = tag;
        mIsServer = isServer;
    }

    @Override
    public void start(final String ip, final int port, final Runnable connectedCallback,
            final Runnable stoppedCallback, final boolean receiveMode) {
        Log.i(mTag, "Start()");
        mIsRunning = true;
        // Better way to handling threading?
        new Thread() {
            public void run() {
                Socket socket = null;
                try {
                    if (mIsServer) {
                        mServerSocket = new ServerSocket(port);
                        mServerSocket.setReuseAddress(true);
                        socket = mServerSocket.accept();
                    } else {
                        socket = new Socket(ip, port);
                    }
                    if (socket == null || !mIsRunning) {
                        return;
                    }

                    mPendingPacketQueue.clear();
                    mIsConnected = true;

                    if (connectedCallback != null) {
                        connectedCallback.run();
                    }

                    final Socket s = socket;
                    if (receiveMode) {
                        new Thread() {
                            public void run() {
                                sendPingLoop(s);
                            }
                        }.start();
                        recvModeLoop(s);
                    } else {
                        new Thread() {
                            public void run() {
                                recvPingLoop(s);
                            }
                        }.start();
                        sendModeLoop(s);
                    }
                    Log.i(mTag, "Client disconnected");
                    mIsConnected = false;
                } catch (IOException ex) {
                    Log.e(mTag, "Server exception: " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    mIsConnected = false;
                    try {
                        if (mServerSocket != null) {
                            mServerSocket.close();
                        }
                    } catch (Exception e){}
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (Exception e){}

                    mStoppingLock.countDown();
                    if (stoppedCallback != null) {
                        stoppedCallback.run();
                    }
                }
            }
        }.start();
    }

    private void sendModeLoop(final Socket socket) {
        try (OutputStream os = socket.getOutputStream()) {
            while (mIsRunning) {
                // TODO: Better busy waiting?
                if (mPendingPacketQueue.size() == 0) {
                    if (socket.isClosed()) {
                        break;
                    }
                    continue;
                }
                if (DEBUG) {
                    debugLog(mTag, "Ready to send, pending size: " + mPendingPacketQueue.size());
                }
                Packet packet = mPendingPacketQueue.poll();

                // Header: Length of packet
                byte[] header = new byte[4];
                header[0] =  (byte) ((packet.size >> 24) & 0xff);
                header[1] =  (byte) ((packet.size >> 16) & 0xff);
                header[2] =  (byte) ((packet.size >> 8) & 0xff);
                header[3] =  (byte) ((packet.size >> 0) & 0xff);

                os.write(header);

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
    }

    private void sendPingLoop(final Socket socket) {
        try (OutputStream os = socket.getOutputStream()) {
            while (mIsRunning) {
                SystemClock.sleep(500);
                os.write(0);
                debugLog(mTag, "sendPingLoop");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void recvPingLoop(final Socket socket) {
        try (InputStream is = socket.getInputStream()) {
            while (mIsRunning) {
                if (is.read() < 0) {
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void recvModeLoop(final Socket socket) {
        try (InputStream is = socket.getInputStream()) {
            while (mIsRunning) {
                byte[] header = new byte[4];
                debugLog(mTag, "isRunning: " + mIsRunning);
                while (mIsRunning) {
                    int headerRemain = 4;
                    int headerOffset = 0;
                    while (headerRemain != 0) {
                        if (!mIsRunning) {
                            return;
                        }
                        if (is.available() == 0 && !socket.isClosed() && socket.isConnected()) {
                            SystemClock.sleep(1);
                            continue;
                        }
                        int size = is.read(header, headerOffset, headerRemain);
                        if (size > 0) {
                            headerOffset += size;
                            headerRemain = headerRemain - size;
                        } else if (size < 0 || socket.isClosed()) {
                            return;
                        }
                    }
                    int nextPacketSize =
                            (((header[0] & 0xff) << 24) | ((header[1] & 0xff) << 16) |
                                    ((header[2] & 0xff) << 8) | (header[3] & 0xff));
                    int nextPacketOffset = 0;

                    byte[] buffer = new byte[nextPacketSize];
                    while(nextPacketSize != 0) {
                        if (!mIsRunning) {
                            return;
                        }
                        if (is.available() == 0 && !socket.isClosed() && socket.isConnected()) {
                            SystemClock.sleep(1);
                            debugLog(mTag, "nextPacketSize: " + nextPacketSize);
                            continue;
                        }
                        int size = is.read(buffer, nextPacketOffset, nextPacketSize);
                        if (size > 0) {
                            nextPacketOffset += size;
                            nextPacketSize = nextPacketSize - size;
                        } else if (size < 0 || socket.isClosed()) {
                            return;
                        }
                    }
                    mPendingPacketQueue.add(new Packet(buffer, nextPacketOffset));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Log.w(mTag, "Stopped..");
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public void stop() {
        mIsRunning = false;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendBuf(byte[] buf, int len) {
        // TODO: Better buf limit ?
        if (mPendingPacketQueue.size() >= 200) {
            debugLog(mTag, "Buffer full, mPendingPacketQueue size: " + mPendingPacketQueue.size());
            return;
        }
        // TODO: need extra copy?
        byte[] bytes = new byte[len];
        System.arraycopy(buf, 0, bytes, 0, len);
        mPendingPacketQueue.add(new Packet(bytes, len));
    }

    @Override
    public boolean isConnected() {
        return mIsConnected;
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

    @Override
    public Packet getNextPacket() {
        return mPendingPacketQueue.poll();
    }

    @Override
    public int packetQueueSize() {
        return mPendingPacketQueue.size();
    }

    private long lastLogTime = 0;
    private void debugLog(String tag, String msg) {
        long time = System.currentTimeMillis();
        if (time - lastLogTime < 1000) {
            return;
        }
        lastLogTime = time;
        Log.i(tag, msg);
    }
}
