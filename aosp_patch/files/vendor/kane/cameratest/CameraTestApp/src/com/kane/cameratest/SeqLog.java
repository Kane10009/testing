package com.kane.cameratest;

import android.os.SystemClock;
import android.util.Log;

import java.util.Locale;

/** Logs the on/off sequence steps under one tag so their order is easy to check. */
final class SeqLog {
    static final String TAG = "CamSeq";

    static void step(String msg) {
        Log.i(TAG, String.format(Locale.US, "uptime=%dms %s", SystemClock.uptimeMillis(), msg));
    }

    private SeqLog() {}
}
