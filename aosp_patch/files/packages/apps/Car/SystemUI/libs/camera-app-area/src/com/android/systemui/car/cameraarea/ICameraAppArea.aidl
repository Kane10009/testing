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

import android.os.Bundle;

/**
 * Lets the camera app hide/show every activity (launcher + apps) of a display while its
 * hole-punch camera window is shown. Implemented by CarSystemUI, which owns the leash of the
 * default TaskDisplayArea through WMShell's RootTaskDisplayAreaOrganizer.
 *
 * Both calls return a Bundle. When {@code deferApply} is true and the leash is ready, the Bundle
 * holds a SurfaceControl.Transaction under {@link CameraAppAreaContract#KEY_TRANSACTION} that the
 * caller must apply itself (e.g. with AttachedSurfaceControl#applyTransactionOnDraw) so the change
 * lands in the same frame as its own window. Otherwise SystemUI applies the change immediately,
 * or as soon as the leash appears.
 */
interface ICameraAppArea {
    /**
     * Hides all apps on {@code displayId}. {@code clientToken} is watched with linkToDeath: if the
     * caller dies, SystemUI shows the apps again so they never stay hidden.
     */
    Bundle hideApps(int displayId, IBinder clientToken, boolean deferApply);

    /** Shows all apps on {@code displayId} again. */
    Bundle showApps(int displayId, boolean deferApply);

    /** Whether SystemUI currently wants the apps on {@code displayId} hidden. */
    boolean isAppsHidden(int displayId);
}
