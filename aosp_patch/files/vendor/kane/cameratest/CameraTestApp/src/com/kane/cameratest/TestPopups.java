package com.kane.cameratest;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Popups for z-order tests around the camera window (layer 12):
 * <ul>
 *   <li>below: TYPE_APPLICATION_OVERLAY (layer 11) - outside the TDA, so hiding the TDA does
 *       not hide it and it shows through the transparent hole;</li>
 *   <li>above: TYPE_SYSTEM_ERROR (layer 27) - always drawn over the camera UI.</li>
 * </ul>
 * Both are NOT_FOCUSABLE so the camera window keeps focus (and keeps the bars hidden).
 */
final class TestPopups {
    private static final String TAG = "TestPopups";

    private final Context mAppContext;
    private final Display mDisplay;

    @Nullable
    private Popup mBelow;
    @Nullable
    private Popup mAbove;

    TestPopups(Context appContext, int displayId) {
        mAppContext = appContext;
        mDisplay = appContext.getSystemService(DisplayManager.class).getDisplay(displayId);
    }

    void toggleBelow() {
        if (mBelow != null) {
            mBelow.dismiss();
            mBelow = null;
            return;
        }
        mBelow = new Popup(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                R.string.popup_below_text, 0xE6D32F2F, /* yOffsetDp= */ 140,
                /* touchable= */ false);
    }

    void toggleAbove() {
        if (mAbove != null) {
            mAbove.dismiss();
            mAbove = null;
            return;
        }
        mAbove = new Popup(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                R.string.popup_above_text, 0xE62E7D32, /* yOffsetDp= */ -140,
                /* touchable= */ true);
    }

    void dismissAll() {
        if (mBelow != null) {
            mBelow.dismiss();
            mBelow = null;
        }
        if (mAbove != null) {
            mAbove.dismiss();
            mAbove = null;
        }
    }

    private final class Popup {
        private final WindowManager mWm;
        private final View mView;
        private final String mName;

        @SuppressWarnings("deprecation")
        Popup(int type, int textRes, int color, int yOffsetDp, boolean touchable) {
            final Context ctx = mAppContext.createDisplayContext(mDisplay)
                    .createWindowContext(type, null);
            mWm = ctx.getSystemService(WindowManager.class);
            mName = type == WindowManager.LayoutParams.TYPE_SYSTEM_ERROR ? "above" : "below";

            final float density = ctx.getResources().getDisplayMetrics().density;
            final TextView tv = new TextView(ctx);
            tv.setText(textRes);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(color);
            final int pad = (int) (24 * density);
            tv.setPadding(pad, pad, pad, pad);
            mView = tv;

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            if (touchable) {
                tv.setOnClickListener(v -> {
                    // Only the "above" popup is touchable: tapping it closes it.
                    if (mAbove == this) {
                        toggleAbove();
                    }
                });
            } else {
                flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    (int) (640 * density), WindowManager.LayoutParams.WRAP_CONTENT, type, flags,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            lp.y = (int) (yOffsetDp * density);
            lp.setFitInsetsTypes(0);
            lp.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
            lp.setTitle("CameraTestPopup-" + mName);
            mWm.addView(mView, lp);
            Log.i(TAG, "Popup " + mName + " shown");
        }

        void dismiss() {
            mWm.removeViewImmediate(mView);
            Log.i(TAG, "Popup " + mName + " dismissed");
        }
    }
}
