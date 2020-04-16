package dev.hihi.virtualmobilevrphone;

import android.content.Context;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;

public class RotationListener {

    private static final String TAG = "RotationListener";

    private OrientationEventListener mListener = null;
    private int currentRotation = 0;

    public interface RotationChangeInterface {
        void onRotationChanged(int rotation);
    }

    public void startListener(final Context context, final RotationChangeInterface callback) {
        final WindowManager wm = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        currentRotation = wm.getDefaultDisplay().getRotation();
        mListener = new OrientationEventListener(context) {

                    @Override
                    public void onOrientationChanged(int i) {
                        int rotation = wm.getDefaultDisplay().getRotation();
                        if (currentRotation != rotation) {
                            mListener.disable();
                            currentRotation = rotation;
                            Log.i(TAG, "RICKYXXX onOrientationChanged: " + currentRotation);
                            callback.onRotationChanged(currentRotation);
                        }
                    }
                };
        mListener.enable();
    }

    public void stopListener() {
        if (mListener != null) {
            mListener.disable();
        }
    }
}
