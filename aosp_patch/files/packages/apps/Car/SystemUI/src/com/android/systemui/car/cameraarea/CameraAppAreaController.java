/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.car.cameraarea;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DisplayAreaInfo;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Hides/shows the whole default TaskDisplayArea (launcher + every app) while the camera app shows
 * its hole-punch window, so the transparent hole reveals the camera hardware plane instead of the
 * apps behind it.
 *
 * <p>Re-uses the leash owned by WMShell's {@link RootTaskDisplayAreaOrganizer}; only one organizer
 * may exist per display-area feature, so registering another one would break the Shell.
 *
 * <p>All state is touched on the organizer's executor (the Shell main thread; on car
 * {@code config_enableShellMainThread} is true, so that is not the SysUI main thread).
 */
@SysUISingleton
public class CameraAppAreaController implements CoreStartable {
    private static final String TAG = "CameraAppArea";
    private static final long BINDER_CALL_TIMEOUT_MS = 2000;

    private final Context mContext;
    @Nullable
    private final RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    private final SparseArray<DisplayState> mDisplays = new SparseArray<>();

    @Inject
    public CameraAppAreaController(Context context,
            Optional<RootTaskDisplayAreaOrganizer> rootTDAOrganizer) {
        mContext = context;
        mRootTDAOrganizer = rootTDAOrganizer.orElse(null);
    }

    @Override
    public void start() {
        if (mRootTDAOrganizer == null) {
            Slog.w(TAG, "Disabled: no RootTaskDisplayAreaOrganizer (not the system user)");
            return;
        }
        // Only the main display for now; other displays are tracked on first request.
        runOnShellThread(() -> getOrCreateState(Display.DEFAULT_DISPLAY));
    }

    boolean isAvailable() {
        return mRootTDAOrganizer != null;
    }

    /**
     * Hides the apps on {@code displayId}. Returns the transaction to apply when
     * {@code deferApply} is true and the leash is ready; otherwise applies it and returns null.
     */
    @Nullable
    SurfaceControl.Transaction hideApps(int displayId, @Nullable IBinder clientToken,
            boolean deferApply) {
        return callOnShellThread(() -> {
            final DisplayState state = getOrCreateState(displayId);
            state.setClient(clientToken);
            return setAppsHidden(state, true, deferApply, "hideApps");
        });
    }

    /** Shows the apps on {@code displayId} again. See {@link #hideApps}. */
    @Nullable
    SurfaceControl.Transaction showApps(int displayId, boolean deferApply) {
        return callOnShellThread(() -> {
            final DisplayState state = getOrCreateState(displayId);
            state.setClient(null);
            return setAppsHidden(state, false, deferApply, "showApps");
        });
    }

    boolean isAppsHidden(int displayId) {
        final Boolean hidden = callOnShellThread(() -> {
            final DisplayState state = mDisplays.get(displayId);
            return state != null && state.mAppsHidden;
        });
        return hidden != null && hidden;
    }

    /** Must run on the shell thread. */
    @Nullable
    private SurfaceControl.Transaction setAppsHidden(DisplayState state, boolean hidden,
            boolean deferApply, String reason) {
        state.mAppsHidden = hidden;
        state.mLastChangeUptimeMs = SystemClock.uptimeMillis();
        state.mLastChangeReason = reason;
        if (hidden) {
            checkWallpaper();
        }
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        if (!mRootTDAOrganizer.setDisplayAreaVisibility(t, state.mDisplayId, !hidden)) {
            // Leash not ready yet: the desired state is applied in onDisplayAreaAppeared().
            Slog.i(TAG, reason + " display=" + state.mDisplayId + ": leash not ready, deferred");
            t.close();
            return null;
        }
        Slog.i(TAG, reason + " display=" + state.mDisplayId + " deferApply=" + deferApply);
        if (deferApply) {
            return t;
        }
        t.apply();
        t.close();
        return null;
    }

    /**
     * The wallpaper is not inside the TDA, so hiding the TDA does not hide it. Car builds turn the
     * wallpaper service off; warn if a build turned it back on outside emulator test mode, where
     * the wallpaper deliberately stands in for the camera plane.
     */
    private void checkWallpaper() {
        if (SystemProperties.getBoolean(CameraAppAreaContract.PROP_EMULATOR_TEST, false)) {
            return;
        }
        if (mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableWallpaperService)) {
            Slog.w(TAG, "Wallpaper service is enabled: the wallpaper may show through the camera"
                    + " hole if a visible window requests it");
        }
    }

    @NonNull
    private DisplayState getOrCreateState(int displayId) {
        DisplayState state = mDisplays.get(displayId);
        if (state == null) {
            state = new DisplayState(displayId);
            mDisplays.put(displayId, state);
            // Calls onDisplayAreaAppeared() right away if the TDA already exists.
            mRootTDAOrganizer.registerListener(displayId, state);
        }
        return state;
    }

    private void runOnShellThread(Runnable r) {
        mRootTDAOrganizer.getExecutor().execute(r);
    }

    /** Runs {@code c} on the shell thread and waits for it; for binder calls. */
    @Nullable
    private <T> T callOnShellThread(Callable<T> c) {
        if (mRootTDAOrganizer == null) {
            return null;
        }
        final FutureTask<T> task = new FutureTask<>(c);
        runOnShellThread(task);
        try {
            return task.get(BINDER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Slog.e(TAG, "Shell thread call failed", e);
            return null;
        }
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("CameraAppAreaController:");
        pw.println("  available=" + isAvailable()
                + " emulatorTest=" + SystemProperties.get(CameraAppAreaContract.PROP_EMULATOR_TEST));
        if (!isAvailable()) {
            return;
        }
        final String states = callOnShellThread(() -> {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mDisplays.size(); i++) {
                sb.append("  ").append(mDisplays.valueAt(i)).append('\n');
            }
            return sb.toString();
        });
        pw.print(states);
    }

    /** Per-display state; also the TDA listener of that display. Shell thread only. */
    private class DisplayState implements RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener,
            IBinder.DeathRecipient {
        final int mDisplayId;
        boolean mAppsHidden;
        boolean mLeashReady;
        @Nullable
        IBinder mClient;
        long mLastChangeUptimeMs;
        String mLastChangeReason = "none";

        DisplayState(int displayId) {
            mDisplayId = displayId;
        }

        void setClient(@Nullable IBinder client) {
            if (mClient == client) {
                return;
            }
            if (mClient != null) {
                mClient.unlinkToDeath(this, 0);
            }
            mClient = null;
            if (client != null) {
                try {
                    client.linkToDeath(this, 0);
                    mClient = client;
                } catch (RemoteException e) {
                    // Already dead and no death notification will come: undo the hide that
                    // follows this call (binderDied() posts, so it runs after it).
                    Slog.w(TAG, "Client already dead, apps will be shown again");
                    binderDied();
                }
            }
        }

        @Override
        public void binderDied() {
            runOnShellThread(() -> {
                if (mClient != null && mClient.isBinderAlive()) {
                    return; // A new client took over before this notification ran.
                }
                Slog.w(TAG, "Camera client died, showing apps on display " + mDisplayId);
                mClient = null;
                setAppsHidden(this, false, false, "clientDied");
            });
        }

        @Override
        public void onDisplayAreaAppeared(DisplayAreaInfo displayAreaInfo) {
            mLeashReady = true;
            // A fresh leash is visible; re-apply a pending or previous hide request.
            if (mAppsHidden) {
                setAppsHidden(this, true, false, "leashAppeared");
            }
        }

        @Override
        public void onDisplayAreaVanished(DisplayAreaInfo displayAreaInfo) {
            mLeashReady = false;
        }

        @Override
        public String toString() {
            return "display=" + mDisplayId + " appsHidden=" + mAppsHidden
                    + " leashReady=" + mLeashReady + " client=" + mClient
                    + " last=" + mLastChangeReason + "@" + mLastChangeUptimeMs;
        }
    }
}
