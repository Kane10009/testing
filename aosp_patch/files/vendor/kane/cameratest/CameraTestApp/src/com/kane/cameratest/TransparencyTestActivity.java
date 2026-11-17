package com.kane.cameratest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * Button 3. An opaque, bright activity. It lands in the DefaultTaskDisplayArea, which is hidden
 * while the camera is on, so it must NOT be visible through the hole. It must appear once the
 * camera is turned off.
 */
public class TransparencyTestActivity extends Activity {
    private static final String TAG = "TransparencyTest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transparency_test);
        findViewById(R.id.transparency_close).setOnClickListener(v -> finish());
        Log.i(TAG, "onCreate task=" + getTaskId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }
}
