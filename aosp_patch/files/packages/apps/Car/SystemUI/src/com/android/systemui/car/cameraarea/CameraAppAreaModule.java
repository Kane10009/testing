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

import android.app.Service;

import com.android.systemui.CoreStartable;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/** Dagger bindings for hiding the app area under the camera hole-punch window. */
@Module
public abstract class CameraAppAreaModule {

    /** Starts {@link CameraAppAreaController} with the other SysUI CoreStartables. */
    @Binds
    @IntoMap
    @ClassKey(CameraAppAreaController.class)
    public abstract CoreStartable bindCameraAppAreaController(CameraAppAreaController controller);

    /** Lets SystemUIAppComponentFactory inject {@link CameraAppAreaService}. */
    @Binds
    @IntoMap
    @ClassKey(CameraAppAreaService.class)
    public abstract Service bindCameraAppAreaService(CameraAppAreaService service);
}
