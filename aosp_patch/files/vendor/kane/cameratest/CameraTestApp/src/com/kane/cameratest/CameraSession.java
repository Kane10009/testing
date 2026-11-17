package com.kane.cameratest;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;

import com.kane.cameratest.subcore.SubCoreCameraClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Camera on/off state machine. Main thread only.
 *
 * <pre>
 * ON : 1. hideApps (SystemUI)  2. add camera window  3. showCamera(rect) (sub-core)
 *      Steps 1 and 2 land in the same frame: the hide transaction is applied with the window's
 *      first frame. Step 3 runs once that frame is committed.
 * OFF: 1. hideCamera (sub-core)  2. camera UI gone + showApps in the same frame
 *      3. window removed after that frame is committed
 * </pre>
 */
final class CameraSession implements AppAreaClient.Callback, CameraWindow.Callbacks {
    private static final String TAG = "CameraSession";
    private static final int DISPLAY_ID = Display.DEFAULT_DISPLAY;
    /** Show the camera even if SystemUI is not reachable: the camera has priority. */
    private static final long SERVICE_WAIT_TIMEOUT_MS = 1000;

    private enum State { OFF, WAITING_FOR_SYSTEMUI, ON, TURNING_OFF }

    @Nullable
    private static CameraSession sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Executor mBackground = Executors.newSingleThreadExecutor();
    private final AppAreaClient mAppArea;
    private final TestPopups mPopups;

    private State mState = State.OFF;
    private boolean mEmulatorTest;
    @Nullable
    private SubCoreCameraClient mSubCore;
    @Nullable
    private CameraWindow mWindow;
    private boolean mCameraShown;
    /** SHOW arrived while the previous session was still turning off. */
    private boolean mPendingShow;
    private int mSessionCount;

    private final Runnable mServiceTimeout = () -> {
        Log.w(TAG, "SystemUI did not connect in " + SERVICE_WAIT_TIMEOUT_MS
                + "ms, showing the camera without hiding the apps");
        startShowing();
    };

    static CameraSession get(Context context) {
        if (sInstance == null) {
            sInstance = new CameraSession(context.getApplicationContext());
        }
        return sInstance;
    }

    private CameraSession(Context appContext) {
        mContext = appContext;
        mAppArea = new AppAreaClient(appContext, this);
        mPopups = new TestPopups(appContext, DISPLAY_ID);
    }

    boolean isOn() {
        return mState == State.ON || mState == State.WAITING_FOR_SYSTEMUI;
    }

    void show() {
        if (mState == State.TURNING_OFF) {
            mPendingShow = true;
            return;
        }
        if (mState != State.OFF) {
            Log.i(TAG, "show() ignored in state " + mState);
            return;
        }
        mSessionCount++;
        mEmulatorTest = CameraTestConfig.isEmulatorTest();
        mSubCore = CameraTestConfig.createSubCoreClient(mEmulatorTest);
        SeqLog.step("=== CAMERA ON #" + mSessionCount + " emulatorTest=" + mEmulatorTest
                + " subCore=" + mSubCore.getClass().getSimpleName());
        mAppArea.bind();
        if (mAppArea.isConnected()) {
            startShowing();
        } else {
            mState = State.WAITING_FOR_SYSTEMUI;
            mHandler.postDelayed(mServiceTimeout, SERVICE_WAIT_TIMEOUT_MS);
        }
    }

    private void startShowing() {
        mHandler.removeCallbacks(mServiceTimeout);
        mState = State.ON;
        mCameraShown = false;
        SeqLog.step("1. hideApps (deferred, applied with the window's first frame)");
        final SurfaceControl.Transaction hideTx = mAppArea.hideApps(DISPLAY_ID, true);
        SeqLog.step("2. add camera window (hideTx " + (hideTx != null
                ? "applied in the same frame" : "null: applied by SystemUI or unavailable")
                + ")");
        mWindow = new CameraWindow(mContext, DISPLAY_ID, mEmulatorTest, this);
        mWindow.show(hideTx);
    }

    void hide() {
        switch (mState) {
            case TURNING_OFF:
                mPendingShow = false;
                return;
            case OFF:
                Log.i(TAG, "hide() ignored in state " + mState);
                return;
            case WAITING_FOR_SYSTEMUI:
                mHandler.removeCallbacks(mServiceTimeout);
                mState = State.OFF;
                SeqLog.step("=== CAMERA OFF before it was shown");
                return;
            case ON:
                break;
        }
        mState = State.TURNING_OFF;
        SeqLog.step("=== CAMERA OFF #" + mSessionCount);
        if (mCameraShown) {
            SeqLog.step("1. hideCamera");
            mSubCore.hideCamera();
            mCameraShown = false;
        }
        mPopups.dismissAll();
        final SurfaceControl.Transaction showTx = mAppArea.showApps(DISPLAY_ID, true);
        SeqLog.step("2. camera UI gone + showApps in the same frame (showTx "
                + (showTx != null ? "deferred" : "null") + ")");
        final CameraWindow window = mWindow;
        mWindow = null;
        window.remove(showTx, () -> {
            SeqLog.step("3. camera window removed");
            mState = State.OFF;
            if (mPendingShow) {
                mPendingShow = false;
                show();
            }
        });
    }

    @Override
    public void onAppAreaServiceConnected(boolean reconnected) {
        if (mState == State.WAITING_FOR_SYSTEMUI) {
            startShowing();
        } else if (mState == State.ON && reconnected) {
            // SystemUI restarted: WM gave the TDA a new, visible surface. Hide it again.
            SeqLog.step("SystemUI restarted while camera on -> hideApps again");
            mAppArea.hideApps(DISPLAY_ID, false);
        }
    }

    @Override
    public void onFirstFrameCommitted(Rect holeRect) {
        if (mState != State.ON) {
            return;
        }
        SeqLog.step("3. showCamera rect=" + holeRect.toShortString());
        mSubCore.showCamera(holeRect);
        mCameraShown = true;
    }

    @Override
    public void onHoleRectChanged(Rect holeRect) {
        if (mState == State.ON && mCameraShown) {
            SeqLog.step("updateRect rect=" + holeRect.toShortString());
            mSubCore.updateRect(holeRect);
        }
    }

    @Override
    public void onPopupBelowClicked() {
        mPopups.toggleBelow();
    }

    @Override
    public void onPopupAboveClicked() {
        mPopups.toggleAbove();
    }

    @Override
    public void onOpenActivityClicked() {
        final Intent intent = new Intent(mContext, TransparencyTestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.i(TAG, "Starting TransparencyTestActivity for the current user");
        // This app runs as the headless system user; the activity goes to the user in front.
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    @Override
    public void onDumpStackClicked() {
        mBackground.execute(() -> {
            final String text = TaskStackDumper.dump(DISPLAY_ID);
            mHandler.post(() -> {
                if (mWindow != null) {
                    mWindow.showStackDump(text);
                }
            });
        });
    }
}
