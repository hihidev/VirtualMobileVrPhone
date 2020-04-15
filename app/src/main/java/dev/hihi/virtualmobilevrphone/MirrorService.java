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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class MirrorService extends Service {

    private static final String TAG = "MirrorService";

    public static final int VIDEO_PORT = 1234;
    public static final int AUDIO_PORT = 1235;

    private MediaProjection mMediaProjection;

    private MirrorServerInterface mAudioServer;
    private MirrorServerInterface mVideoServer;

    private AudioEncoder mAudioEncoder;

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
                sIsRunning = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startAudioStreaming();
                } else {
                    Log.w(TAG, "Cannot support audio streaming");
                }
                break;
            case "stop":
                AudioEncoder audioEncoder = mAudioEncoder;
                if (audioEncoder != null) {
                    audioEncoder.stop();
                }
                sIsRunning = false;
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
                mAudioServer.start(inetSockAddress);
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

    public static boolean isRunning() {
        return sIsRunning;
    }


    public static void setRunning(boolean b) {
        sIsRunning = b;
    }
}
