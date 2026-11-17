package com.kane.cameratest.subcore;

import android.graphics.Rect;

/**
 * Commands to the sub-core (RTOS/Linux) that scans the camera image out on its own hardware
 * plane under Android. Rects are in display pixels, from the hole view's on-screen position.
 */
public interface SubCoreCameraClient {
    /** Starts showing the camera image in {@code rectOnScreen}. */
    void showCamera(Rect rectOnScreen);

    /** Stops showing the camera image. */
    void hideCamera();

    /** The hole moved or was resized while the camera is shown. */
    void updateRect(Rect rectOnScreen);
}
