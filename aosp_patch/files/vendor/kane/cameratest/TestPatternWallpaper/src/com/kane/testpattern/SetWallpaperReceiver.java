package com.kane.testpattern;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Makes {@link TestPatternWallpaperService} the wallpaper of the user receiving the broadcast. */
public class SetWallpaperReceiver extends BroadcastReceiver {
    private static final String TAG = "TestPattern";

    @Override
    public void onReceive(Context context, Intent intent) {
        final WallpaperManager wm = WallpaperManager.getInstance(context);
        if (wm == null || !wm.isWallpaperSupported()) {
            Log.e(TAG, "Wallpaper service disabled: is KaneCameraTestFrameworkOverlay installed?");
            return;
        }
        final ComponentName cn = new ComponentName(context, TestPatternWallpaperService.class);
        final boolean ok = wm.setWallpaperComponent(cn);
        Log.i(TAG, "setWallpaperComponent(" + cn.flattenToShortString() + ") = " + ok
                + " user=" + context.getUserId());
    }
}
