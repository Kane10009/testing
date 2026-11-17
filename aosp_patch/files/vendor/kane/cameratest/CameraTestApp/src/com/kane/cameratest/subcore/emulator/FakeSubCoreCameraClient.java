package com.kane.cameratest.subcore.emulator;

import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import com.kane.cameratest.subcore.SubCoreCameraClient;

import java.util.Locale;

/**
 * Emulator-only stand-in for the sub-core: there is no hardware plane, the live wallpaper plays
 * that role. Only logs every command with its arguments and timestamps so the command order can
 * be checked:
 * <pre>adb logcat -s FakeSubCore:I CamSeq:I CameraAppArea:I</pre>
 */
public final class FakeSubCoreCameraClient implements SubCoreCameraClient {
    private static final String TAG = "FakeSubCore";

    private int mSeq;

    @Override
    public void showCamera(Rect rectOnScreen) {
        log("showCamera rect=" + rectOnScreen.toShortString()
                + " size=" + rectOnScreen.width() + "x" + rectOnScreen.height());
    }

    @Override
    public void hideCamera() {
        log("hideCamera");
    }

    @Override
    public void updateRect(Rect rectOnScreen) {
        log("updateRect rect=" + rectOnScreen.toShortString()
                + " size=" + rectOnScreen.width() + "x" + rectOnScreen.height());
    }

    private void log(String cmd) {
        Log.i(TAG, String.format(Locale.US, "#%d uptime=%dms wall=%d %s",
                ++mSeq, SystemClock.uptimeMillis(), System.currentTimeMillis(), cmd));
    }
}
