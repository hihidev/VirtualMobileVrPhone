package dev.hihi.virtualmobilevrphone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.net.InetSocketAddress;

public class MirrorService extends Service {

    private static final String TAG = "MirrorService";

    public static final int VIDEO_PORT = 1234;
    public static final int AUDIO_PORT = 1235;

    private MediaProjection mMediaProjection;

    private MirrorServerInterface mAudioServer;
    private MirrorServerInterface mVideoServer;

    private AudioEncoder mAudioEncoder;
    private VideoEncoder mVideoEncoder;

    // TODO: Fix it
    private static boolean sIsRunning = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
                mAudioEncoder = new AudioEncoder();
                InetSocketAddress inetSockAddress = new InetSocketAddress(AUDIO_PORT);
                mAudioServer = new TcpServer();
                mAudioServer.start("AudioServer", inetSockAddress);
                mAudioEncoder.streamAudio(mMediaProjection, mAudioServer);
                mAudioEncoder.waitUntilStopped();
                Log.i(TAG, "AudioEncoder stopped, stopping Audio TCP server");
                mAudioServer.stop();
                mAudioServer.waitUntilStopped();
                Log.i(TAG, "Audio TCP server stopped");
                mAudioEncoder = null;
                mAudioServer = null;
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
                    final RotationListener rotationListener = new RotationListener();
                    rotationListener.startListener(getApplicationContext(),
                            new RotationListener.RotationChangeInterface() {
                                @Override
                                public void onRotationChanged(int rotation) {
                                    rotationListener.stopListener();
                                    VideoEncoder encoder = mVideoEncoder;
                                    if (encoder != null) {
                                        encoder.stop();
                                    }
                                }
                            });
                    InetSocketAddress inetSockAddress = new InetSocketAddress(VIDEO_PORT);
                    mVideoServer = new TcpServer();
                    mVideoServer.start("VideoServer", inetSockAddress, new Runnable() {
                        @Override
                        public void run() {
                            VideoEncoder encoder = mVideoEncoder;
                            if (encoder != null) {
                                encoder.stop();
                            }
                        }
                    });
                    Log.i(TAG, "Start video stream");
                    mVideoEncoder.start(mMediaProjection, width, height, density, mVideoServer);
                    Log.i(TAG, "VideoEncoder stopped, stopping Video TCP server");
                    rotationListener.stopListener();
                    mVideoServer.stop();
                    mVideoServer.waitUntilStopped();
                    Log.i(TAG, "Video TCP server stopped");
                    mVideoServer = null;
                    mVideoEncoder = null;
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
