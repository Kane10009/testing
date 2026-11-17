package com.kane.cameratest;

import android.os.SystemProperties;

import com.android.systemui.car.cameraarea.CameraAppAreaContract;
import com.kane.cameratest.subcore.RealSubCoreCameraClient;
import com.kane.cameratest.subcore.SubCoreCameraClient;
import com.kane.cameratest.subcore.emulator.FakeSubCoreCameraClient;

/** Emulator test mode switch; read each time the camera is turned on. */
final class CameraTestConfig {

    /** {@code adb shell setprop persist.debug.camera.emulator_test 1} */
    static boolean isEmulatorTest() {
        return SystemProperties.getBoolean(CameraAppAreaContract.PROP_EMULATOR_TEST, false);
    }

    /** Fake (log only) client in emulator test mode, real IPC client otherwise. */
    static SubCoreCameraClient createSubCoreClient(boolean emulatorTest) {
        return emulatorTest ? new FakeSubCoreCameraClient() : new RealSubCoreCameraClient();
    }

    private CameraTestConfig() {}
}
