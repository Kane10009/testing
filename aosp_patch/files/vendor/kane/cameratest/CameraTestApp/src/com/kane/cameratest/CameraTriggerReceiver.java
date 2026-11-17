package com.kane.cameratest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

/**
 * adb trigger, instead of a reverse-gear signal:
 * <pre>
 * adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
 * adb shell am broadcast --user 0 -a com.kane.cameratest.action.HIDE -p com.kane.cameratest
 * </pre>
 */
public class CameraTriggerReceiver extends BroadcastReceiver {
    private static final String TAG = "CameraTrigger";

    static final String ACTION_SHOW = "com.kane.cameratest.action.SHOW";
    static final String ACTION_HIDE = "com.kane.cameratest.action.HIDE";
    static final String ACTION_TOGGLE = "com.kane.cameratest.action.TOGGLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
            // CarSystemUI (the TDA owner) runs as the system user; so does the camera window.
            Log.w(TAG, "Ignored in user " + UserHandle.myUserId());
            return;
        }
        final CameraSession session = CameraSession.get(context);
        final String action = intent.getAction();
        Log.i(TAG, "onReceive " + action);
        if (ACTION_SHOW.equals(action)) {
            session.show();
        } else if (ACTION_HIDE.equals(action)) {
            session.hide();
        } else if (ACTION_TOGGLE.equals(action)) {
            if (session.isOn()) {
                session.hide();
            } else {
                session.show();
            }
        }
    }
}
