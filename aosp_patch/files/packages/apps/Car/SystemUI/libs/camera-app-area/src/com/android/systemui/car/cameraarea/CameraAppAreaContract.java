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

import android.content.ComponentName;

/** Constants shared by CarSystemUI and the camera app for {@link ICameraAppArea}. */
public final class CameraAppAreaContract {

    /** Signature permission required to bind {@link #SERVICE_COMPONENT}. */
    public static final String PERMISSION =
            "com.android.systemui.car.permission.CONTROL_CAMERA_APP_AREA";

    /** The CarSystemUI service implementing {@link ICameraAppArea}. */
    public static final ComponentName SERVICE_COMPONENT = new ComponentName(
            "com.android.systemui",
            "com.android.systemui.car.cameraarea.CameraAppAreaService");

    /** Bundle key of the SurfaceControl.Transaction returned when deferApply is true. */
    public static final String KEY_TRANSACTION = "transaction";

    /**
     * Emulator test mode switch, read on every camera on/off. When "1": the camera app uses the
     * fake sub-core client and the wallpaper is left visible (it stands in for the camera plane).
     */
    public static final String PROP_EMULATOR_TEST = "persist.debug.camera.emulator_test";

    private CameraAppAreaContract() {}
}
