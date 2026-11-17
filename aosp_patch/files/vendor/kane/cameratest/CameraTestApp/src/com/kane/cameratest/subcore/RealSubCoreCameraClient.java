package com.kane.cameratest.subcore;

import android.graphics.Rect;
import android.util.Log;

/**
 * STUB: the real IPC to the sub-core is not implemented yet. Replace the bodies with the vendor
 * IPC (HIDL/AIDL HAL, rpmsg, ...). Must not block the main thread for long: the camera window
 * waits for these calls.
 */
public final class RealSubCoreCameraClient implements SubCoreCameraClient {
    private static final String TAG = "RealSubCore";

    @Override
    public void showCamera(Rect rectOnScreen) {
        Log.w(TAG, "TODO IPC showCamera " + rectOnScreen.toShortString());
    }

    @Override
    public void hideCamera() {
        Log.w(TAG, "TODO IPC hideCamera");
    }

    @Override
    public void updateRect(Rect rectOnScreen) {
        Log.w(TAG, "TODO IPC updateRect " + rectOnScreen.toShortString());
    }
}
