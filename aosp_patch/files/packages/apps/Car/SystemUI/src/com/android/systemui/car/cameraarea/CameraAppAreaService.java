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

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceControl;

import javax.inject.Inject;

/**
 * Binder entry point of {@link CameraAppAreaController} for the camera app. Protected by
 * {@link CameraAppAreaContract#PERMISSION} (signature) in the manifest and on every call.
 */
public class CameraAppAreaService extends Service {

    private final CameraAppAreaController mController;

    private final ICameraAppArea.Stub mBinder = new ICameraAppArea.Stub() {
        @Override
        public Bundle hideApps(int displayId, IBinder clientToken, boolean deferApply) {
            enforcePermission();
            return toBundle(mController.hideApps(displayId, clientToken, deferApply));
        }

        @Override
        public Bundle showApps(int displayId, boolean deferApply) {
            enforcePermission();
            return toBundle(mController.showApps(displayId, deferApply));
        }

        @Override
        public boolean isAppsHidden(int displayId) {
            enforcePermission();
            return mController.isAppsHidden(displayId);
        }
    };

    @Inject
    public CameraAppAreaService(CameraAppAreaController controller) {
        mController = controller;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mController.isAvailable() ? mBinder : null;
    }

    private void enforcePermission() {
        enforceCallingPermission(CameraAppAreaContract.PERMISSION, "ICameraAppArea");
    }

    private static Bundle toBundle(@Nullable SurfaceControl.Transaction t) {
        final Bundle b = new Bundle();
        if (t != null) {
            b.putParcelable(CameraAppAreaContract.KEY_TRANSACTION, t);
        }
        return b;
    }
}
