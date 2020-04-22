package dev.hihi.virtualmobilevrphone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Path;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.CountDownLatch;

public class CommandService {

    private static final String TAG = "CommandService";

    public static class COMMAND {
        public static final int UNKNOWN = 0;
        public static final int GESTURE = 1;
    }

    public static class GESTURE {
        public static final int UNKNOWN = 0;
        public static final int ACTION_MOVE = 1;
        public static final int ACTION_UP = 2;
        public static final int ACTION_DOWN = 3;
    }

    private final AccessibilityService mAccessibilityService;
    private GestureDescription.StrokeDescription mLastStrokeDescription = null;
    private float mLastX, mLastY;
    private boolean mIsRunning = false;

    private CountDownLatch mStoppingLock = new CountDownLatch(1);

    public CommandService(AccessibilityService accessibilityService) {
        this.mAccessibilityService = accessibilityService;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void performGesture(float x, float y, int action) {
        Log.i(TAG, "performGesture: " + x + ", " + y + ", " + action);
        boolean willContinue;
        Path clickPath = new Path();
        switch (action) {
            case GESTURE.ACTION_MOVE:
                clickPath.moveTo(mLastX, mLastY);
                clickPath.lineTo(x, y);
                willContinue = true;
                break;
            case GESTURE.ACTION_UP:
                clickPath.moveTo(mLastX, mLastY);
                clickPath.lineTo(x, y);
                willContinue = false;
                break;
            case GESTURE.ACTION_DOWN:
                clickPath.moveTo(x, y);
                willContinue = true;
                break;
            default:
                Log.e(TAG, "unknown action: " + action);
                return;

        }
        if (action == GESTURE.ACTION_DOWN) {
            wakeScreenIfNecessary();
        }
        if (mLastStrokeDescription == null) {
            mLastStrokeDescription = new GestureDescription.StrokeDescription(clickPath, 0, 1, willContinue);
        } else {
            mLastStrokeDescription = mLastStrokeDescription.continueStroke(clickPath, 0, 1, willContinue);
        }
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(mLastStrokeDescription);
        GestureDescription description = clickBuilder.build();

        final CountDownLatch latch = new CountDownLatch(1);
        AccessibilityService.GestureResultCallback callback = new AccessibilityService.GestureResultCallback() {
            public void onCompleted(GestureDescription gestureDescription) {
                latch.countDown();
            }

            public void onCancelled(GestureDescription gestureDescription) {
                latch.countDown();
            }
        };
        mAccessibilityService.dispatchGesture(description, callback, null);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mLastX = x;
        mLastY = y;
        if (!willContinue) {
            mLastStrokeDescription = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void start(final MirrorServerInterface server) {
        mIsRunning = true;
        new Thread() {
            public void run() {
                try {
                    while (mIsRunning) {
                        Packet packet = server.getNextPacket();
                        // TODO: No busy waiting
                        if (packet == null) {
                            SystemClock.sleep(1);
                            continue;
                        }
                        int command = packet.bytes[0];
                        switch (command) {
                            case COMMAND.GESTURE:
                                int action = packet.bytes[1];
                                int x = (packet.bytes[2] & 0xff) * 256 + (packet.bytes[3] & 0xff);
                                int y = (packet.bytes[4] & 0xff) * 256 + (packet.bytes[5] & 0xff);
                                performGesture(x, y, action);
                                break;
                            default:
                                Log.e(TAG, "unknown command: " + command);
                                return;
                        }
                    }
                } finally {
                    mStoppingLock.countDown();
                }
            }
        }.start();
    }

    private void wakeScreenIfNecessary() {
        PowerManager pm = (PowerManager) mAccessibilityService.getSystemService(Context.POWER_SERVICE);
        if (pm.isInteractive()) {
            return;
        }
        @SuppressLint("InvalidWakeLockTag")
        PowerManager.WakeLock screenLock = ((PowerManager) mAccessibilityService.getSystemService(
                Context.POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");
        screenLock.acquire();
        screenLock.release();
    }

    public void stop() {
        mIsRunning = false;
    }

    public void waitUntilStopped() {
        try {
            mStoppingLock.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
