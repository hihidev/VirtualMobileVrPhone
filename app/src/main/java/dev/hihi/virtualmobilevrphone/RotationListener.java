package dev.hihi.virtualmobilevrphone;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;

public class RotationListener {

    private static final String TAG = "RotationListener";

    private Context mContext;
    private SensorEventListener mListener = null;
    private int currentRotation = 0;

    public interface RotationChangeInterface {
        void onRotationChanged(int rotation);
    }

    public void startListener(final Context context, final RotationChangeInterface callback) {
        mContext = context.getApplicationContext();
        final WindowManager wm = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        currentRotation = wm.getDefaultDisplay().getRotation();
        final SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mListener = new SensorEventListener() {

                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        int rotation = wm.getDefaultDisplay().getRotation();
                        if (currentRotation != rotation) {
                            currentRotation = rotation;
                            callback.onRotationChanged(currentRotation);
                            unregisterListener();
                        }
                    }
                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };
        sm.registerListener(mListener, sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);

    }

    public void stopListener() {
        unregisterListener();
    }

    private synchronized void unregisterListener() {
        if (mListener != null && mContext != null) {
            final SensorManager sm = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            sm.unregisterListener(mListener);
            mListener = null;
        }
    }
}
