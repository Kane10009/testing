package com.kane.cameratest;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.SurfaceControl;

import com.android.systemui.car.cameraarea.CameraAppAreaContract;
import com.android.systemui.car.cameraarea.ICameraAppArea;

/**
 * Connection to CarSystemUI's CameraAppAreaService, which hides/shows the TaskDisplayArea.
 * Stays bound once bound: when SystemUI restarts, the framework reconnects automatically and
 * {@link Callback#onAppAreaServiceConnected} fires again with {@code reconnected = true}.
 * Main thread only.
 */
final class AppAreaClient implements ServiceConnection {
    private static final String TAG = "AppAreaClient";

    interface Callback {
        void onAppAreaServiceConnected(boolean reconnected);
    }

    private final Context mContext;
    private final Callback mCallback;
    /** Identifies this process to SystemUI, which shows the apps again if it dies. */
    private final IBinder mClientToken = new Binder();

    @Nullable
    private ICameraAppArea mService;
    private boolean mBound;
    private boolean mEverConnected;

    AppAreaClient(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    void bind() {
        if (mBound) {
            return;
        }
        final Intent intent = new Intent().setComponent(CameraAppAreaContract.SERVICE_COMPONENT);
        mBound = mContext.bindServiceAsUser(intent, this,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.SYSTEM);
        if (!mBound) {
            Log.e(TAG, "Cannot bind " + CameraAppAreaContract.SERVICE_COMPONENT
                    + " (CarSystemUI without the camera app area patch?)");
        }
    }

    boolean isConnected() {
        return mService != null;
    }

    /**
     * Hides the apps. With {@code deferApply}, returns the transaction the caller must apply
     * together with its first window frame; null if SystemUI applied it (or will once ready).
     */
    @Nullable
    SurfaceControl.Transaction hideApps(int displayId, boolean deferApply) {
        if (mService == null) {
            Log.w(TAG, "hideApps: SystemUI not connected, apps stay visible");
            return null;
        }
        try {
            return getTransaction(mService.hideApps(displayId, mClientToken, deferApply));
        } catch (RemoteException e) {
            Log.e(TAG, "hideApps failed", e);
            return null;
        }
    }

    /** Shows the apps again. See {@link #hideApps}. */
    @Nullable
    SurfaceControl.Transaction showApps(int displayId, boolean deferApply) {
        if (mService == null) {
            // A dead SystemUI shows the apps anyway: WM gives the TDA a fresh, visible surface.
            Log.w(TAG, "showApps: SystemUI not connected");
            return null;
        }
        try {
            return getTransaction(mService.showApps(displayId, deferApply));
        } catch (RemoteException e) {
            Log.e(TAG, "showApps failed", e);
            return null;
        }
    }

    @Nullable
    private static SurfaceControl.Transaction getTransaction(@Nullable Bundle b) {
        return b == null ? null : b.getParcelable(CameraAppAreaContract.KEY_TRANSACTION,
                SurfaceControl.Transaction.class);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = ICameraAppArea.Stub.asInterface(service);
        Log.i(TAG, "Connected to SystemUI (reconnected=" + mEverConnected + ")");
        final boolean reconnected = mEverConnected;
        mEverConnected = true;
        mCallback.onAppAreaServiceConnected(reconnected);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.w(TAG, "SystemUI disconnected; waiting for it to restart");
        mService = null;
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.w(TAG, "Binding died, rebinding");
        mService = null;
        mContext.unbindService(this);
        mBound = false;
        bind();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        Log.e(TAG, "SystemUI returned no binder (no RootTaskDisplayAreaOrganizer in that user)");
    }
}
