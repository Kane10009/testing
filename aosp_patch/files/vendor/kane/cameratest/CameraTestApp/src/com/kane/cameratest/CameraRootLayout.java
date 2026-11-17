package com.kane.cameratest;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * Root of the camera window. The window is focused (needed to hide the system bars), so it also
 * receives keys: swallow BACK so it never reaches the hidden app below.
 */
public class CameraRootLayout extends FrameLayout {

    public CameraRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                Log.i(SeqLog.TAG, "BACK swallowed by camera window");
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
