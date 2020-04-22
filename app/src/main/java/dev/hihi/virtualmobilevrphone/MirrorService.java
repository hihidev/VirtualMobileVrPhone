package dev.hihi.virtualmobilevrphone;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

public class MirrorService extends AccessibilityService {

    private static final String TAG = "MirrorService";

    public static final int VIDEO_PORT = 1234;
    public static final int AUDIO_PORT = 1235;
    public static final int COMMAND_PORT = 1236;

    private MediaProjection mMediaProjection;

    private MirrorServerInterface mAudioServer;
    private MirrorServerInterface mVideoServer;
    private MirrorServerInterface mCommandServer;

    private AudioEncoder mAudioEncoder;
    private VideoEncoder mVideoEncoder;
    private CommandService mCommandService;

    private NsdHelper mNsdHelper;

    // TODO: Fix it
    private static boolean sIsRunning = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }

    @Override
    public void onInterrupt() {

    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "VirtualMobile",
                    "VirtualMobile Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "VirtualMobile")
                .setContentTitle("VirtualMobile Service")
                .setContentText("VirtualMobile is running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        startForeground(8964, notification);
        mNsdHelper = new NsdHelper(getApplicationContext(), null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent == null) {
            Log.w(TAG, "onStartCommand empty intent");
            return START_STICKY;
        }

        final String command = intent.getStringExtra("command");
        final int resultCode = intent.getIntExtra("resultCode", 0);
        Log.i(TAG, "onStartCommand resultCode: " + resultCode);
        if (command == null) {
            return START_STICKY;
        }
        switch (command) {
            case "start":
                final Intent data = intent.getParcelableExtra("data");
                final MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = projectionManager.getMediaProjection(resultCode,
                        data);
                if (mMediaProjection == null) {
                    Log.w(TAG, "mMediaProjection is null");
                    return START_STICKY;
                }
                sIsRunning = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startAudioStreaming();
                } else {
                    Log.w(TAG, "Cannot support audio streaming");
                }
                startVideoStreaming();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startCommandService();
                }
                mNsdHelper.registerService(COMMAND_PORT);
                break;
            case "stop":
                sIsRunning = false;
                AudioEncoder audioEncoder = mAudioEncoder;
                if (audioEncoder != null) {
                    audioEncoder.stop();
                }
                MirrorServerInterface audioServer = mAudioServer;
                if (audioServer != null) {
                    audioServer.stop();
                }
                VideoEncoder videoEncoder = mVideoEncoder;
                if (videoEncoder != null) {
                    videoEncoder.stop();
                }
                MirrorServerInterface videoServer = mVideoServer;
                if (videoServer != null) {
                    videoServer.stop();
                }
                CommandService commandService = mCommandService;
                if (commandService != null) {
                    commandService.stop();
                }
                MirrorServerInterface commandServer = mCommandServer;
                if (commandServer != null) {
                    commandServer.stop();
                }
                mNsdHelper.tearDown();
                break;
            default:
                Log.e(TAG, "Unknown command: " + command);
        }
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startAudioStreaming() {
        Log.i(TAG, "startAudioStreaming");
        // TODO: Better thread handling?
        new Thread () {
            public void run() {
                while (sIsRunning) {
                    Log.i(TAG, "startAudioStreaming run()");
                    mAudioEncoder = new AudioEncoder();
                    mAudioServer = new Tcp("AudioServer", true);
                    mAudioServer.start(null, AUDIO_PORT, null, null, false);

                    Log.i(TAG, "Start audio streaming");
                    mAudioEncoder.streamAudio(mMediaProjection, mAudioServer);
                    // Wait for client disconnect
                    mAudioServer.waitUntilStopped();

                    Log.i(TAG, "Audio client disconnected, stopping all audio components");
                    mAudioServer.stop();
                    mAudioEncoder.stop();

                    mAudioServer.waitUntilStopped();
                    mAudioEncoder.waitUntilStopped();

                    Log.i(TAG, "All audio components stopped");
                    mAudioEncoder = null;
                    mAudioServer = null;
                }
            }
        }.start();
    }

    private void startVideoStreaming() {
        // TODO: Better thread handling?
        new Thread () {
            public void run() {
                while (sIsRunning) {
                    final DisplayMetrics metrics = new DisplayMetrics();
                    final WindowManager wm =(WindowManager) getApplicationContext()
                            .getSystemService(Context.WINDOW_SERVICE);
                    wm.getDefaultDisplay().getRealMetrics(metrics);

                    final int density = metrics.densityDpi;
                    final int height = metrics.heightPixels;
                    final int width = metrics.widthPixels;

                    mVideoEncoder = new VideoEncoder();
                    mVideoServer = new Tcp("VideoServer", true);
                    final VideoEncoder videoEncoder = mVideoEncoder;
                    final MirrorServerInterface server = mVideoServer;

                    final RotationListener rotationListener = new RotationListener();
                    rotationListener.startListener(getApplicationContext(),
                            new RotationListener.RotationChangeInterface() {
                                @Override
                                public void onRotationChanged(int rotation) {
                                    rotationListener.stopListener();
                                    // Orientation changed, disconnect client to restart
                                    server.stop();
                                }
                            });
                    mVideoServer.start(null, VIDEO_PORT, new Runnable() {
                        @Override
                        public void run() {
                            videoEncoder.onClientConnected();
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                            // If client disconnected, stop video encoder too.
                            videoEncoder.stop();
                        }
                    }, false);

                    Log.i(TAG, "Start video streaming");
                    mVideoEncoder.start(mMediaProjection, width, height, density, mVideoServer);

                    Log.i(TAG, "VideoEncoder stopped, stopping all video components");
                    rotationListener.stopListener();
                    mVideoEncoder.stop();
                    mVideoServer.stop();

                    // mVideoEncoder.waitUntilStopped();
                    mVideoServer.waitUntilStopped();

                    Log.i(TAG, "All videos component stopped");
                    mVideoServer = null;
                    mVideoEncoder = null;
                }
            }
        }.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startCommandService() {
        new Thread () {
            public void run() {
                while (sIsRunning) {
                    mCommandService = new CommandService(MirrorService.this);
                    mCommandServer = new Tcp("CommandServer", true);

                    mCommandServer.start(null, COMMAND_PORT, null, null, true);
                    mCommandService.start(mCommandServer);

                    Log.i(TAG, "Command server started");
                    mCommandServer.waitUntilStopped();

                    Log.i(TAG, "Command server stopped, stopping Command service");
                    mCommandService.stop();
                    mCommandServer.stop();

                    mCommandService.waitUntilStopped();
                    mCommandServer.waitUntilStopped();

                    Log.i(TAG, "Command service stopped");
                    mCommandService = null;
                    mCommandServer = null;
                }
            }
        }.start();
    }

    public static boolean isRunning() {
        return sIsRunning;
    }


    public static void setRunning(boolean b) {
        sIsRunning = b;
    }
}
