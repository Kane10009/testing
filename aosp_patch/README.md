# Camera hole-punch: ẩn app dưới window camera (AAOS 14, phương án B)

Bản build đích: Android 14 `UP1A.231005.007`, `sdk_car_x86_64-userdebug`, emulator
`emulator_car_x86_64`, có bật headless system user (HSUM).

## 1. Kiến trúc

```
 app camera (com.kane.cameratest, chạy ở user 0)     CarSystemUI (user 0)
 ┌──────────────────────────────┐   binder    ┌─────────────────────────────────┐
 │ CameraSession (máy trạng thái)│ ─────────► │ CameraAppAreaService            │
 │ CameraWindow (TYPE_SYSTEM_ALERT)│ hide/show │   └ CameraAppAreaController     │
 │ SubCoreCameraClient ──► sub-core│ Apps     │       └ RootTaskDisplayAreaOrg. │
 └──────────────────────────────┘            │         (WMShell, giữ leash TDA)│
                                              └─────────────────────────────────┘
```

* **CarSystemUI chỉ làm một việc:** ẩn/hiện leash của `DefaultTaskDisplayArea`, dùng lại
  organizer `RootTaskDisplayAreaOrganizer` có sẵn của WMShell. Không đăng ký organizer thứ hai,
  vì WM sẽ âm thầm thay organizer cũ và làm hỏng Shell.
* **App camera giữ trạng thái và UI.** Nếu app camera chết, SystemUI (qua `linkToDeath`) tự hiện
  lại app. Nếu SystemUI chết, WM cấp cho TDA một surface mới đang hiện; app camera reconnect rồi
  gọi `hideApps` lại.

### Thứ tự lệnh

| Bật camera | Tắt camera |
|---|---|
| 1. `hideApps` (transaction được trả về cho app camera, không apply ngay) | 1. `hideCamera()` gửi sub-core |
| 2. add window camera; transaction ẩn TDA được apply **cùng frame đầu tiên** của window | 2. ẩn UI camera và `showApps` **trong cùng một frame** (window vẫn còn nhưng không vẽ gì) |
| 3. `showCamera(rect)` sau khi frame đầu tiên đã commit | 3. gỡ window sau khi frame đó đã commit |

Bước 2 dùng `AttachedSurfaceControl.applyTransactionOnDraw()`, nên không có frame nào lộ app hoặc
bị trống.

### Ẩn status bar và nav bar
Window camera là window hệ thống và đang giữ focus, nên WM giao quyền điều khiển insets cho chính
nó. (`BarControlPolicy` của CarSystemUI chỉ can thiệp khi window đang focus là window app, type
1–99.) Vì vậy chỉ cần `WindowInsetsController.hide(systemBars())`, giống app xem video full màn
hình. Vuốt từ mép màn hình sẽ hiện thanh tạm thời (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).

### Thứ tự layer (theo `WindowManagerPolicy`)
| Layer | Thành phần |
|---|---|
| 1 | Wallpaper, nằm ngoài TDA (khi test trên emulator, đây là **plane camera giả**) |
| 2 | DefaultTaskDisplayArea, gồm mọi activity, **bị ẩn khi camera bật** |
| 11 | Popup DƯỚI (`TYPE_APPLICATION_OVERLAY`), nút 1 |
| 12 | **Window camera** (`TYPE_SYSTEM_ALERT`) |
| 13 | IME |
| 15 / 24 | Status bar / nav bar (đang bị ẩn) |
| 27 | Popup TRÊN (`TYPE_SYSTEM_ERROR`), nút 2 |

## 2. Các file và lý do sửa

| File | Lý do |
|---|---|
| `frameworks/base/libs/WindowManager/Shell/.../RootTaskDisplayAreaOrganizer.java` | Thêm `setDisplayAreaVisibility()`. Leash TDA là `private` và chỉ organizer của Shell được giữ. |
| `packages/apps/Car/SystemUI/libs/camera-app-area/` (mới) | `ICameraAppArea.aidl` + `CameraAppAreaContract`, thư viện dùng chung cho SystemUI và app camera. |
| `packages/apps/Car/SystemUI/src/.../car/cameraarea/CameraAppAreaController.java` (mới) | `CoreStartable`: lấy leash qua `registerListener`, lưu trạng thái mong muốn theo display, áp dụng lại khi leash xuất hiện, `linkToDeath` client. Chạy trên thread Shell (car bật `config_enableShellMainThread`). |
| `.../cameraarea/CameraAppAreaService.java` (mới) | Binder cho app camera, kiểm tra permission ở mỗi lần gọi. |
| `.../cameraarea/CameraAppAreaModule.java` (mới) | Dagger: đăng ký `CoreStartable` và `Service`. |
| `packages/apps/Car/SystemUI/src/.../CarSysUIComponent.java` | Thêm `CameraAppAreaModule`. |
| `packages/apps/Car/SystemUI/AndroidManifest.xml` | Định nghĩa permission `signature` `CONTROL_CAMERA_APP_AREA` và khai báo service. |
| `packages/apps/Car/SystemUI/Android.bp` | Link `car-camera-app-area-aidl`. |
| `vendor/kane/cameratest/CameraTestApp` (mới) | App camera test: window, 4 nút, sub-core client (fake/stub), trigger qua adb. |
| `vendor/kane/cameratest/TestPatternWallpaper` (mới, **chỉ test**) | Live wallpaper test pattern, đóng vai plane camera. |
| `vendor/kane/cameratest/TouchTestApp` (mới, **chỉ test**) | Lưới nút ghi log, dùng cho test touch. |
| `vendor/kane/cameratest/overlay/EmulatorWallpaperOverlay` (mới, **chỉ test**) | Bật lại `config_enableWallpaperService`. Bản car tắt wallpaper service mặc định. |
| `vendor/kane/cameratest/privapp/` | privapp-permissions. |
| `vendor/kane/cameratest/cameratest.mk` | Danh sách `PRODUCT_PACKAGES`. |

Quyền của app camera (ký platform, privileged, cài vào `system_ext`):
`CONTROL_CAMERA_APP_AREA`, `INTERNAL_SYSTEM_WINDOW`, `SYSTEM_ALERT_WINDOW`,
`MANAGE_ACTIVITY_TASKS`, `DUMP`, `INTERACT_ACROSS_USERS(_FULL)`,
`START_ACTIVITIES_FROM_BACKGROUND`. App camera không cần đăng ký organizer, nên dùng
`MANAGE_ACTIVITY_TASKS` chỉ để liệt kê task (nút 4).

## 3. Build và cài lên emulator

### 3.1 Đưa patch vào cây AOSP (trong máy ảo)
```bash
bash apply_to_aosp.sh ./aosp_patch /home/vboxuser/aosp --dry-run
bash apply_to_aosp.sh ./aosp_patch /home/vboxuser/aosp
```
Thêm 1 dòng vào `vendor/kane/products/kane_car.mk`:
```make
$(call inherit-product, vendor/kane/cameratest/cameratest.mk)
```

### 3.2 Build
```bash
cd /home/vboxuser/aosp
source build/envsetup.sh
lunch sdk_car_x86_64-userdebug
m -j$(nproc)
```
Lần đầu nên build full (`m`), vì có thêm package mới vào product. Các lần sau chỉ cần build
những module đã đổi. WMShell được build tĩnh vào SystemUI, nên sửa `RootTaskDisplayAreaOrganizer`
thì chỉ cần build lại `CarSystemUI`.
```bash
m CarSystemUI KaneCameraTest KaneTestPatternWallpaper KaneTouchTest \
  KaneCameraTestFrameworkOverlay privapp-permissions-kane-cameratest.xml
```

### 3.3 Chạy emulator và đẩy bản build lên
Lần đầu sau khi build full:
```bash
emulator -writable-system -no-snapshot &
```
Khi chỉ cần đẩy lại module đã sửa (emulator phải chạy với `-writable-system`):
```bash
adb root
adb remount          # lần đầu có thể yêu cầu: adb reboot && adb root && adb remount
adb sync system_ext  # CarSystemUI, KaneCameraTest, wallpaper, touch app, privapp xml
adb sync product     # overlay bật wallpaper service
adb reboot
```
Đường dẫn khi cài: `/system_ext/priv-app/CarSystemUI/`, `/system_ext/priv-app/KaneCameraTest/`,
`/system_ext/priv-app/KaneTestPatternWallpaper/`, `/system_ext/app/KaneTouchTest/`,
`/system_ext/etc/permissions/privapp-permissions-kane-cameratest.xml`,
`/product/overlay/KaneCameraTestFrameworkOverlay.apk`.

Kiểm tra sau khi boot:
```bash
adb shell pm list packages | grep -E "kane|systemui"
adb shell cmd overlay list android | grep Kane            # phải là [x]
adb shell dumpsys package com.kane.cameratest | grep -E "INTERNAL_SYSTEM_WINDOW|CONTROL_CAMERA_APP_AREA"
```

## 4. Bật chế độ test emulator
```bash
adb root
adb shell setprop persist.debug.camera.emulator_test 1
adb shell getprop persist.debug.camera.emulator_test        # phải in ra 1
# Đặt live wallpaper test pattern cho user đang dùng (HSUM: user 10)
adb shell am broadcast --user current -a com.kane.testpattern.action.SET -p com.kane.testpattern
adb logcat -d -s TestPattern
```
Property này quyết định 2 việc (đọc lại mỗi lần bật camera, không cần reboot):

| | `=1` (test emulator) | `=0` hoặc chưa đặt (chạy thật) |
|---|---|---|
| Sub-core client | `FakeSubCoreCameraClient` (chỉ ghi log) | `RealSubCoreCameraClient` (**stub**) |
| Window camera | có `FLAG_SHOW_WALLPAPER` (wallpaper là plane giả) | không có |
| Wallpaper | giữ nguyên | không dùng (bản car tắt wallpaper service; SystemUI ghi cảnh báo nếu nó đang bật) |

> **SELinux:** nếu `CamSeq` in ra `emulatorTest=false` dù đã setprop, chạy
> `adb logcat -d | grep avc | grep emulator_test`. Nếu có denial thì tạm thời dùng
> `adb shell setenforce 0`, hoặc thêm sepolicy cho property.

## 5. Bật/tắt camera bằng adb
```bash
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW   -p com.kane.cameratest
adb shell am broadcast --user 0 -a com.kane.cameratest.action.HIDE   -p com.kane.cameratest
adb shell am broadcast --user 0 -a com.kane.cameratest.action.TOGGLE -p com.kane.cameratest
```
Theo dõi log:
```bash
adb logcat -s CamSeq:I FakeSubCore:I CameraAppArea:I AppAreaClient:I CameraWindow:I
```
Kết quả mong đợi khi bật rồi tắt:
```
CamSeq        : === CAMERA ON #1 emulatorTest=true subCore=FakeSubCoreCameraClient
CamSeq        : 1. hideApps (deferred, applied with the window's first frame)
CameraAppArea : hideApps display=0 deferApply=true
CamSeq        : 2. add camera window (hideTx applied in the same frame)
CamSeq        : 3. showCamera rect=[0,0][1920,1080]
FakeSubCore   : #1 uptime=...ms wall=... showCamera rect=[0,0][1920,1080] size=1920x1080
CamSeq        : === CAMERA OFF #1
CamSeq        : 1. hideCamera
FakeSubCore   : #2 ... hideCamera
CameraAppArea : showApps display=0 deferApply=true
CamSeq        : 2. camera UI gone + showApps in the same frame (showTx deferred)
CamSeq        : 3. camera window removed
```

Bốn nút trên màn camera:
1. **Popup DƯỚI** (layer 11, nằm ngoài TDA): sẽ **thấy được qua lỗ**. Đây là giới hạn đã biết.
   Window hệ thống nằm dưới window camera không bị ẩn theo TDA.
2. **Popup TRÊN** (layer 27): luôn nằm trên nút và guideline. Chạm vào popup để đóng.
3. **Mở Activity**: start `TransparencyTestActivity` (màu hồng) cho user hiện tại. Khi camera
   bật thì **không được thấy** activity này; tắt camera thì nó phải hiện ra.
4. **Activity trong stack**: hiện danh sách root task (task trên cùng in trước) và mọi
   `ActivityRecord` theo task (lọc từ `dumpsys activity activities`). Đồng thời ghi ra logcat
   với tag `CameraTest/Stack`. Chạm vào bảng để đóng.

## 6. Kịch bản test

Trước mỗi kịch bản: chế độ test đã bật (mục 4) và log đang chạy.

### 6.1 Cây DisplayArea và organizer
```bash
adb shell dumpsys window containers | grep -nE "DisplayArea|Leaf:0:1|WallpaperWindowToken"
adb shell dumpsys activity service com.android.systemui/.SystemUIService CameraAppAreaController
adb shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | grep -A3 RootTaskDisplayAreaOrganizer
```
Mong đợi:
* `Leaf:0:1` (chứa wallpaper) nằm **dưới** `DefaultTaskDisplayArea`.
* Dump của `CameraAppAreaController` có `available=true` và
  `display=0 appsHidden=false leashReady=true`.
* Chỉ có một organizer cho TDA, là của WMShell. Log không có dòng
  `Replacing dead organizer for feature=1` (`adb logcat -d | grep "Replacing dead organizer"`).

### 6.2 SurfaceFlinger khi camera bật
```bash
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell dumpsys SurfaceFlinger > sf_on.txt
grep -n -A12 "DefaultTaskDisplayArea" sf_on.txt | grep -iE "DefaultTaskDisplayArea|flags=|hidden"
grep -n -A12 "CameraTestWindow" sf_on.txt | grep -iE "CameraTestWindow|isOpaque"
adb exec-out screencap -p > cam_on.png
```
Mong đợi:
* Layer `DefaultTaskDisplayArea` ở trạng thái hidden: `flags=` có bit `0x1` (eLayerHidden),
  hoặc có chữ `hidden`, tùy định dạng dump.
* Layer `CameraTestWindow` có `isOpaque=0`.
* Ảnh `cam_on.png` thấy test pattern có timestamp đang chạy, các nút và guideline; không thấy
  launcher hay app; không có status bar và nav bar.

Tắt camera rồi dump lại: TDA không còn hidden, layer `CameraTestWindow` biến mất.

### 6.3 Touch
```bash
adb shell am start --user current -n com.kane.touchtest/.TouchTestActivity
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell input tap 960 540        # chạm vào vùng lỗ, chỗ có nút của TouchTest
adb shell input keyevent BACK      # phím Back cũng phải bị nuốt
adb logcat -d -s TouchTest:I CameraWindow:I CamSeq:I
```
Mong đợi: có dòng `CameraWindow: Hole touch #1 at 960,540 swallowed` và
`BACK swallowed by camera window`; **không có** dòng `TouchTest: CLICK`. Bộ đếm trên màn hình
tăng lên. Tắt camera rồi tap lại thì phải có `TouchTest: CLICK button N`.

### 6.4 Rect khi đổi độ phân giải / density
```bash
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell wm size 1280x720
adb shell wm density 120
adb shell wm size reset
adb shell wm density reset
adb logcat -d -s FakeSubCore:I
```
Mong đợi: sau mỗi lần `wm size`, có một dòng `updateRect rect=[0,0][1280,720] size=1280x720`
(và quay về kích thước gốc khi reset). Chỉ đổi `wm density` thì rect tính bằng pixel không đổi,
nên **không có** `updateRect`. Đây là hành vi đúng.

### 6.5 Guard: app khác được mở trong lúc camera bật
```bash
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell am start --user current -a android.settings.SETTINGS
```
Mong đợi:
* Settings **không** hiện qua lỗ, status bar và nav bar vẫn ẩn, window camera vẫn giữ focus
  (`adb shell dumpsys window | grep mCurrentFocus` trả về `CameraTestWindow`).
* Nút 4 cho thấy Settings đã nằm trên cùng của stack.
* Tắt camera thì Settings hiện ra.
* Làm tương tự với nút 3 (`TransparencyTestActivity`).

### 6.6 Độ bền: bật/tắt 50 lần
```bash
adb shell dumpsys SurfaceFlinger --list | wc -l     # ghi lại số layer TRƯỚC
adb logcat -c
for i in $(seq 1 50); do
  adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest > /dev/null
  sleep 1.5
  adb shell am broadcast --user 0 -a com.kane.cameratest.action.HIDE -p com.kane.cameratest > /dev/null
  sleep 1.5
done
adb logcat -d -s CamSeq:I | grep -c "camera window removed"          # = 50
adb logcat -d | grep -E "FATAL|AndroidRuntime|Shell thread call failed"  # không có dòng nào
adb shell dumpsys window windows | grep -c CameraTestWindow           # = 0
adb shell dumpsys SurfaceFlinger --list | wc -l                       # xấp xỉ số TRƯỚC
adb shell dumpsys meminfo --user 0 com.kane.cameratest | grep -E "Views|ViewRootImpl"   # ViewRootImpl: 0
adb shell dumpsys activity service com.android.systemui/.SystemUIService CameraAppAreaController
```
Mong đợi: dòng cuối cùng in ra `appsHidden=false client=null`.

### 6.7 Kill SystemUI trong lúc camera bật
```bash
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell pkill -f com.android.systemui
adb logcat -s AppAreaClient:I CamSeq:I CameraAppArea:I
```
Mong đợi:
```
AppAreaClient : SystemUI disconnected; waiting for it to restart
AppAreaClient : Connected to SystemUI (reconnected=true)
CamSeq        : SystemUI restarted while camera on -> hideApps again
CameraAppArea : hideApps display=0 deferApply=false     (hoặc "leash not ready, deferred" rồi "leashAppeared")
```
Trong khoảng SystemUI đang khởi động lại (1–3 giây), app sẽ **lộ qua lỗ**, vì WM đã cấp cho
TDA một surface mới đang hiện. Đây là giới hạn của phương án B (xem mục 7). Sau đó TDA bị ẩn lại;
tắt camera thì app hiện lại bình thường.

### 6.8 Kill app camera trong lúc camera bật (failsafe)
```bash
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell am force-stop --user 0 com.kane.cameratest
adb logcat -d -s CameraAppArea:W
```
Mong đợi: có dòng `Camera client died, showing apps on display 0`, và launcher/app hiện lại ngay.
App camera là `persistent`, nên hệ thống tự khởi động lại tiến trình ngay sau đó; camera ở trạng
thái tắt cho tới lệnh SHOW tiếp theo. Có thể dùng
`adb shell kill $(adb shell pidof com.kane.cameratest)` thay cho `force-stop`.

## 7. Giới hạn và những phần còn để stub

**Còn là stub hoặc chưa làm:**
* `RealSubCoreCameraClient`: IPC thật tới sub-core (**stub**, chỉ ghi log `TODO IPC`).
* Tín hiệu bật camera thật (số lùi, VHAL `GEAR_SELECTION`): hiện chỉ trigger được qua adb.
* Lấy webcam host qua Camera2 cho wallpaper (tùy chọn): chưa làm, wallpaper đang vẽ test pattern.
* Sepolicy cho `persist.debug.camera.emulator_test`, nếu bản build của bạn chặn app đọc property này.

**Giới hạn của phương án B:**
* Window hệ thống nằm **dưới** window camera và ngoài TDA (`TYPE_APPLICATION_OVERLAY`, toast)
  vẫn lộ qua lỗ (nút 1 minh họa điều này). IME (layer 13) nằm **trên** window camera.
* Khi SystemUI restart, app lộ qua lỗ cho đến khi app camera reconnect (mục 6.7).
* Vuốt từ mép màn hình sẽ hiện thanh tạm thời. Muốn khóa hẳn thì phải sửa chính sách trong
  CarSystemUI.

**Những thứ emulator không test được:**
* Blend plane thật: ARGB/XRGB, premultiplied alpha, zpos.
* HWC/DPU của vendor (composition thật của lỗ alpha 0 với plane camera).
* Độ trễ camera thật và đồng bộ giữa hai OS.
* Early RVC lúc boot (camera phải hiện trước khi Android lên).
* Đổi zpos khi vào chế độ SAFETY.
