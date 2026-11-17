#
# Camera hole-punch test kit (EMULATOR / BRING-UP ONLY).
#
# Include from vendor/kane/products/kane_car.mk:
#   $(call inherit-product, vendor/kane/cameratest/cameratest.mk)
#
# Do not include in production images: KaneCameraTestFrameworkOverlay turns the wallpaper
# service back on, and the apps expose adb-triggered test hooks.
#

PRODUCT_PACKAGES += \
    KaneCameraTest \
    KaneTestPatternWallpaper \
    KaneTouchTest \
    KaneCameraTestFrameworkOverlay \
    privapp-permissions-kane-cameratest.xml
