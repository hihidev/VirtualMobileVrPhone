package dev.hihi.virtualmobilevrphone;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_RECORD_REQUEST_CODE = 831;
    private static final int MEDIA_PROJECTION_REQUEST_CODE = 721;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.start_stop_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!audioPermissionGranted()) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                            AUDIO_RECORD_REQUEST_CODE);
                } else if (MirrorService.isRunning()) {
                    startService(false, 0, null);
                    MirrorService.setRunning(false);
                    updateUI();
                } else {
                    startProjection();
                }
            }
        });
    }

    private void startProjection() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(),
                MEDIA_PROJECTION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            startService(true, resultCode, data);
        }
    }

    private void startService(boolean startServer, int resultCode, Intent data) {
        Intent intent = new Intent();
        intent.setClass(this, MirrorService.class);
        intent.putExtra("command", startServer ? "start" : "stop");
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        MirrorService.setRunning(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        if (!audioPermissionGranted()) {
            updateButtonText("Grand permission");
        } else if (MirrorService.isRunning()) {
            updateButtonText("Stop server");
        } else {
            updateButtonText("Start server");
        }
    }

    private void updateButtonText(String text) {
        ((TextView) findViewById(R.id.start_stop_btn)).setText(text);
    }

    private boolean audioPermissionGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Not supported
            return true;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }
}
