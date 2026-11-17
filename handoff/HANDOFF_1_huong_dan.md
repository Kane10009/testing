# HANDOFF: Ẩn toàn bộ app dưới window camera hole-punch trên AAOS (phương án B)

> Tài liệu này dùng để dán vào một phiên Claude Code mới, nhằm làm lại đúng giải pháp đã làm và
> test trên emulator. Phần 1 là bối cảnh và yêu cầu, phần 2 là kết quả khảo sát (có dẫn chứng),
> phần 3 là thiết kế, phần 4 là các bẫy đã gặp, phần 5 là danh sách file và cách làm từng file,
> phần 6 là build và test.
> Mã nguồn đầy đủ của từng file nằm trong `HANDOFF_2_ma_nguon.md`.
>
> **Gửi Claude ở phiên mới:** hãy khảo sát cây AOSP thật trước (mục 2.6), rồi báo kết quả cho
> người dùng trước khi sửa code lớn. Cây của công ty có thể khác version hoặc đã tùy biến
> SystemUI. Mọi giả định trong tài liệu này đã được kiểm chứng trên **Android 14
> `UP1A.231005.007`, `sdk_car_x86_64-userdebug`**.

---

## 1. Bối cảnh và yêu cầu

### 1.1 Hệ thống
* Head unit ô tô chạy 2 hệ điều hành trên cùng một màn hình:
  * **Android (AAOS)** vẽ toàn bộ UI.
  * **Sub-core** (RTOS/Linux) đưa hình camera thẳng vào một **plane phần cứng** của DPU. Android
    không xử lý pixel camera.
* Plane camera nằm **dưới** plane Android. Muốn thấy camera thì vùng đó của Android phải trong
  suốt (alpha = 0), gọi là "hole punch".
* Người dùng có quyền build AOSP, ký platform, và sửa CarSystemUI.

### 1.2 Vấn đề
UI camera là một **window hệ thống** chứ không phải Activity. WindowManager vì vậy **không tự ẩn**
launcher và các app bên dưới, nên vùng lỗ trong suốt để lộ launcher thay vì plane camera.
Không thể ép app khác vẽ trong suốt, nên giải pháp là **ẩn hẳn container chứa mọi activity**
(`DefaultTaskDisplayArea`, viết tắt TDA) trong lúc camera bật.

### 1.3 Phương án đã chọn: B
* **CarSystemUI** chỉ làm một việc: ẩn/hiện leash của TDA, và cung cấp một service AIDL nhỏ.
* **App camera riêng** (ký platform, privileged) giữ trạng thái và UI camera, gọi SystemUI qua
  binder.
* Không chọn phương án A (đặt UI camera trong SystemUI) vì muốn tách module: app camera crash
  không kéo SystemUI theo.
* Không chọn phương án C (sửa WM core trong system_server) vì phải sửa framework quá sâu.
* Phương án dự phòng (một Activity rỗng "bịt nền") không dùng, vì nó thay đổi task stack và có
  thể bị app khác đè lên.

### 1.4 Yêu cầu chức năng
1. Camera bật: vùng lỗ hiện hình camera (trên emulator là test pattern), không thấy
   launcher/app. Window camera **full màn hình và ẩn status bar, nav bar** (giống YouTube full
   màn hình).
2. Chạm vào vùng camera không được kích hoạt app bị ẩn bên dưới. Phím Back cũng không được lọt
   xuống.
3. Camera tắt: app hiện lại đúng trạng thái cũ, không nháy.
4. Bật/tắt 50 lần không lỗi, không leak. Kill SystemUI thì trạng thái được khôi phục.
5. Thứ tự khi bật: `hideApps` → hiện window camera → `showCamera(rect)`.
   Thứ tự khi tắt: `hideCamera` → gỡ window → `showApps`. Không được nháy hình.
6. Interface `SubCoreCameraClient` gồm `showCamera(rect)`, `hideCamera()`, `updateRect(rect)`.
   Rect lấy từ `getLocationOnScreen` và kích thước của view vùng lỗ, cập nhật khi layout đổi.
   IPC thật để stub; bản fake chỉ ghi log có timestamp.
7. Chế độ test emulator bật/tắt bằng system property. Property này quyết định dùng client fake
   hay thật, và có giữ wallpaper hay không.
8. Trigger bật/tắt bằng adb broadcast.
9. App camera test có **4 nút dọc bên trái**:
   * Nút 1: popup ở **layer DƯỚI** window camera.
   * Nút 2: popup ở **layer TRÊN** window camera.
   * Nút 3: mở một Activity để kiểm tra độ trong suốt.
   * Nút 4: hiện danh sách activity/task đang có trong stack.

---

## 2. Kết quả khảo sát (đã kiểm chứng trên Android 14)

### 2.1 Organizer của TDA
* Chỉ có **một** organizer đăng ký `FEATURE_DEFAULT_TASK_CONTAINER`:
  `frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/RootTaskDisplayAreaOrganizer.java`.
  Ở Android 14, nó gọi `registerOrganizer(...)` ngay trong constructor.
* **Đăng ký trùng sẽ âm thầm phá Shell.** Trong
  `services/core/java/com/android/server/wm/DisplayAreaOrganizerController.registerOrganizer`:
  ```java
  if (mOrganizersByFeatureIds.get(feature) != null) {
      mOrganizersByFeatureIds.remove(feature).destroy();   // organizer cũ bị đá ra
  ```
  Organizer cũ nhận `onDisplayAreaVanished` và mất leash. **Tuyệt đối không đăng ký organizer
  thứ hai.**
* Leash nằm trong `private final SparseArray<SurfaceControl> mLeashes`. Listener
  `RootTaskDisplayAreaListener` chỉ trả về `DisplayAreaInfo`, không kèm leash. Cả 3 nhánh
  android14, android15 và main đều không có getter cho leash. **Vì vậy phải thêm một hàm public**
  vào lớp này.
* `registerListener(displayId, listener)` gọi `onDisplayAreaAppeared` ngay lập tức nếu DA đã có
  sẵn. Callback của listener chỉ báo cho đúng display đã đăng ký.
* `DisplayAreaOrganizer.getExecutor()` là public, và chính là executor của Shell main thread.

### 2.2 CarSystemUI đã có sẵn đường inject organizer
* `CarSystemUIInitializer.prepareSysUIComponentBuilder` gọi
  `setRootTaskDisplayAreaOrganizer(isSystemUser ? Optional.of(carWm.getRootTaskDisplayAreaOrganizer()) : Optional.empty())`.
* `CarSysUIComponent` dùng `@BindsInstance Optional<RootTaskDisplayAreaOrganizer>`.
* Mẫu tham khảo: `car/cluster/ClusterDisplayController.java` là một `CoreStartable` và dùng
  `registerListener`.
* `CoreStartable` được đăng ký bằng
  `@Binds @IntoMap @ClassKey(X.class) abstract CoreStartable bind(X)`.
  `CoreStartable` kế thừa `Dumpable` và có `dump(PrintWriter, String[])`.
* Service trong SystemUI được inject thông qua map `@Binds @IntoMap @ClassKey(X.class) abstract Service bind(X)`
  (xem `DefaultServiceBinder`, được include trong `CarComponentBinder`, nằm trong
  `CarSysUIComponent`).
* **`config_enableShellMainThread = true` trên car** (`packages/apps/Car/SystemUI/res/values/config.xml`).
  Shell chạy trên thread riêng, **không phải** SysUI main thread. Mọi truy cập organizer và leash
  phải đi qua `organizer.getExecutor()`.

### 2.3 Hành vi khi organizer chết (SystemUI crash)
`DisplayAreaOrganizerController` → `destroy()` → `da.setOrganizer(null)` →
`sendDisplayAreaVanished` → `migrateToNewSurfaceControl()`, và hàm này gọi
`setInitialSurfaceControlProperties()` → `show(mSurfaceControl)`.
Hệ quả: **SystemUI chết thì TDA tự hiện lại**, nên app không bị kẹt ẩn vĩnh viễn. Ngược lại,
SystemUI sau khi khởi động lại phải được báo để ẩn lại. Trong phương án B, app camera là nơi
giữ trạng thái và sẽ gọi `hideApps` lại khi reconnect.

### 2.4 Ẩn status bar và nav bar
`InsetsPolicy.remoteInsetsControllerControlsSystemBars(focusedWin)` chỉ trả về true khi window
đang focus là **window app** (type `FIRST_APPLICATION_WINDOW`..`LAST_APPLICATION_WINDOW`).
* Với app thường trên AAOS, quyền điều khiển thanh thuộc về `DisplaySystemBarsController` +
  `BarControlPolicy` của CarSystemUI (setting `SYSTEM_BAR_VISIBILITY_OVERRIDE`). Lời gọi `hide()`
  từ app bị bỏ qua.
* Với **window hệ thống đang focus**, control target chính là window đó, nên
  `WindowInsetsController.hide(systemBars())` có tác dụng.
* Điều kiện: window camera phải **focusable** (không có `FLAG_NOT_FOCUSABLE`). Các popup phải có
  `NOT_FOCUSABLE`.

### 2.5 Thứ tự layer (`WindowManagerPolicy.getWindowLayerFromTypeLw`)
| Type | Layer |
|---|---|
| Wallpaper | 1 |
| App (TDA) | 2 |
| `TYPE_APPLICATION_OVERLAY` | 11 |
| `TYPE_SYSTEM_ALERT` (có `INTERNAL_SYSTEM_WINDOW`) | 12 |
| IME | 13 |
| Status bar | 15 |
| Nav bar | 24 |
| `TYPE_SYSTEM_ERROR` (có `INTERNAL_SYSTEM_WINDOW`) | 27 |

Lựa chọn: window camera dùng `TYPE_SYSTEM_ALERT` (12), popup dưới dùng
`TYPE_APPLICATION_OVERLAY` (11), popup trên dùng `TYPE_SYSTEM_ERROR` (27).
Wallpaper nằm ở `Leaf:0:1`, là anh em ngay dưới `DefaultTaskDisplayArea`, không nằm trong TDA.

### 2.6 Lệnh khảo sát cần chạy lại trên cây của công ty
```bash
grep -rn "FEATURE_DEFAULT_TASK_CONTAINER" frameworks/base/libs/WindowManager packages/apps/Car packages/services/Car device vendor --include=*.java --include=*.kt | grep -v /tests/
grep -rn "extends DisplayAreaOrganizer\|registerOrganizer(" frameworks/base/libs/WindowManager packages vendor --include=*.java --include=*.kt | grep -v /tests/
grep -rn "config_deviceSpecificDisplayAreaPolicyProvider" frameworks/base/core/res/res/values/config.xml packages device vendor --include=*.xml
grep -rn "enableShellMainThread" packages/apps/Car/SystemUI/res*
grep -rlE 'name:\s*"[A-Za-z]*SystemUI' packages device vendor --include=Android.bp   # SystemUI tùy biến?
grep -rn "headless_system_user" device packages/services/Car/car_product/build
grep -rn "config_enableWallpaperService" packages/services/Car/car_product device vendor --include=*.xml
grep -n "PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS" device/generic/car/*.mk
```
Những điểm cần xác nhận:
* Chỉ có organizer của WMShell đăng ký TDA.
* Không có DisplayAreaPolicy riêng.
* Product đang dùng **SystemUI nào**: trên máy của người dùng từng có một `MyCarSystemUI` thay
  thế `CarSystemUI`. Nếu có bản tùy biến thì phải biết nó có dùng lại `CarSystemUI-core` và
  `CarSysUIComponent` không, và có cùng package `com.android.systemui` không.
* HSUM có bật không: trên emulator car 14 là **có**, nên current user là 10.
* Wallpaper service có bị tắt không: bản car tắt (`false`).

---

## 3. Thiết kế

```
 App camera (com.kane.cameratest, user 0, persistent)   CarSystemUI (user 0)
 ┌───────────────────────────────┐   binder    ┌─────────────────────────────────┐
 │ CameraTriggerReceiver (adb)   │            │ CameraAppAreaService (signature)│
 │ CameraSession (state machine) │ ─────────► │  └ CameraAppAreaController      │
 │ AppAreaClient (bind, token)   │ hide/show  │      (CoreStartable, shell thr.) │
 │ CameraWindow (TYPE_SYSTEM_ALERT)│ Apps     │      └ RootTaskDisplayAreaOrg.  │
 │ TestPopups, TaskStackDumper   │            │        .setDisplayAreaVisibility│
 │ SubCoreCameraClient → sub-core│            └─────────────────────────────────┘
 └───────────────────────────────┘
```

### 3.1 AIDL `ICameraAppArea` (thư viện dùng chung `car-camera-app-area-aidl`)
```aidl
Bundle hideApps(int displayId, IBinder clientToken, boolean deferApply);
Bundle showApps(int displayId, boolean deferApply);
boolean isAppsHidden(int displayId);
```
* Khi `deferApply = true` và leash đã sẵn sàng, Bundle chứa `SurfaceControl.Transaction` dưới
  key `"transaction"`. `Transaction` là Parcelable nên truyền được qua binder, và **dùng Bundle
  để khỏi phải khai báo parcelable trong AIDL**. App camera tự apply transaction cùng frame của
  window mình qua `AttachedSurfaceControl.applyTransactionOnDraw()`.
* Khi `deferApply = false`, SystemUI apply ngay. Nếu leash chưa sẵn sàng thì SystemUI lưu trạng
  thái mong muốn và apply khi `onDisplayAreaAppeared`.
* `clientToken` là một `new Binder()` của app camera. SystemUI gọi `linkToDeath`: app camera chết
  thì tự `showApps`. Trong `binderDied`, kiểm tra `mClient.isBinderAlive()` để không xử lý nhầm
  khi đã có client mới thay thế.
* Hằng số dùng chung đặt trong `CameraAppAreaContract`: permission, `ComponentName` của service,
  key của Bundle, tên property test.

### 3.2 SystemUI
* `CameraAppAreaController` (`@SysUISingleton`, `CoreStartable`):
  * State theo display (`SparseArray<DisplayState>`). `DisplayState` đồng thời là
    `RootTaskDisplayAreaListener` và `IBinder.DeathRecipient`.
  * `start()` đăng ký display 0. Display khác được đăng ký khi có yêu cầu đầu tiên.
  * Lời gọi binder → `FutureTask` chạy trên `organizer.getExecutor()`, chờ với timeout 2 giây.
  * Ở chế độ chạy thật, nếu `config_enableWallpaperService` đang bật thì ghi cảnh báo, vì
    wallpaper nằm ngoài TDA.
  * `dump()` in trạng thái theo display.
* `CameraAppAreaService`: `onBind` trả về `null` nếu controller không khả dụng (process không
  phải user 0). Gọi `enforceCallingPermission` ở mỗi method.
* `CameraAppAreaModule`: `@Binds @IntoMap @ClassKey` cho cả `CoreStartable` và `Service`.
  Thêm module này vào `CarSysUIComponent`.
* Manifest: định nghĩa permission `signature` `com.android.systemui.car.permission.CONTROL_CAMERA_APP_AREA`,
  và khai báo service với `exported=true`, `singleUser=true` và permission đó.
* WMShell: thêm `boolean setDisplayAreaVisibility(Transaction tx, int displayId, boolean visible)`.

### 3.3 App camera
* Package `com.kane.cameratest`, cài vào `system_ext`, privileged, ký platform,
  **`android:persistent="true"`** (xem bẫy 4.7).
* Chỉ xử lý trigger ở **user 0**, cùng user với SystemUI. Receiver có `singleUser=true` và kiểm
  tra `UserHandle.myUserId()`. Receiver được bảo vệ bằng permission `DUMP` (adb shell có quyền
  này).
* `CameraSession`, máy trạng thái `OFF → WAITING_FOR_SYSTEMUI → ON → TURNING_OFF`:
  * **Bật:** `hideApps(defer=true)` → tạo `CameraWindow.show(hideTx)`. Transaction được apply
    trong `onViewAttachedToWindow` bằng `getRootSurfaceControl().applyTransactionOnDraw(tx)`,
    nên ẩn app và hiện window **cùng một frame**. Sau đó
    `ViewTreeObserver.registerFrameCommitCallback` → `showCamera(rect)`.
  * **Tắt:** `hideCamera()` → `showApps(defer=true)` → ẩn **các view con** (không ẩn root, vì ẩn
    root làm window relayout và WM ẩn surface trong một transaction khác) → apply transaction
    hiện app cùng frame → `addTransactionCommittedListener` → `removeViewImmediate`. Có timeout
    an toàn 500 ms.
  * Nếu SystemUI chưa kết nối thì chờ tối đa 1 giây, sau đó vẫn hiện camera (camera được ưu
    tiên).
  * Nếu SystemUI reconnect khi camera đang bật thì gọi `hideApps(defer=false)` lại.
  * Nếu lệnh SHOW đến lúc đang `TURNING_OFF` thì xếp hàng, chạy sau khi gỡ window xong.
* `CameraWindow`:
  * `TYPE_SYSTEM_ALERT`, `MATCH_PARENT`, `PixelFormat.TRANSLUCENT`, background `null`.
  * Flags `FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED`; thêm `FLAG_SHOW_WALLPAPER` khi ở
    chế độ test.
  * `setFitInsetsTypes(0)` và `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, để rect không nhảy khi
    thanh hệ thống ẩn/hiện.
  * `privateFlags |= SYSTEM_FLAG_SHOW_FOR_ALL_USERS` (HSUM, xem bẫy 4.4).
  * `windowAnimations = 0`.
  * Tạo context bằng `createDisplayContext(display).createWindowContext(type, null)` rồi bọc
    trong `ContextThemeWrapper`.
  * Sau `addView`: `getWindowInsetsController().hide(systemBars())` với behavior
    `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
  * Layout gồm:
    * view `camera_hole` full màn hình, trong suốt; `OnTouchListener` luôn trả `true` (nuốt
      touch) và đếm số lần chạm;
    * `GuidelineView` (vạch đỗ xe, không clickable);
    * TextView đếm số lần chạm;
    * cột 4 nút bên trái;
    * `ScrollView` hiện kết quả nút 4.
  * Root `CameraRootLayout` nuốt `KEYCODE_BACK`.
  * Rect: `OnGlobalLayoutListener` → `getLocationOnScreen` + kích thước → chỉ gọi `updateRect`
    khi rect thật sự đổi.
* `TestPopups`:
  * Popup dưới: `TYPE_APPLICATION_OVERLAY`, `NOT_FOCUSABLE | NOT_TOUCHABLE`.
  * Popup trên: `TYPE_SYSTEM_ERROR`, `NOT_FOCUSABLE`, chạm để đóng.
  * Cả hai có `SYSTEM_FLAG_SHOW_FOR_ALL_USERS` và `setFitInsetsTypes(0)`.
* Nút 3: `startActivityAsUser(intent NEW_TASK, UserHandle.CURRENT)` mở
  `TransparencyTestActivity` (nền hồng đậm, opaque). Khi camera bật thì **không được thấy**
  activity này.
* Nút 4 (`TaskStackDumper`, chạy ở thread nền):
  * `ActivityTaskManager.getService().getAllRootTaskInfosOnDisplay(0)`: windowing mode, activity
    type, visible, top activity, child task kèm userId.
  * Thêm các dòng `TaskDisplayArea` / `* Task{` / `* Hist` lấy từ
    `ServiceManager.getService("activity").dumpAsync(pipe, {"activities"})`.
  * Ghi log với tag `CameraTest/Stack`.
* `SubCoreCameraClient`:
  * `FakeSubCoreCameraClient` (package `subcore.emulator`) ghi log tag `FakeSubCore`, kèm số thứ
    tự, uptime và wall time.
  * `RealSubCoreCameraClient` là STUB, ghi log `TODO IPC`.
  * Mỗi lần bật camera đều chọn lại theo property.
* `SeqLog` ghi log tag `CamSeq` cho từng bước trong thứ tự bật/tắt.

### 3.4 Bộ công cụ test emulator (không đưa vào bản chạy thật)
* `KaneTestPatternWallpaper`: live wallpaper vẽ lưới màu đổi sắc, vạch trắng chạy ngang, và
  timestamp đến mili-giây kèm số frame, khoảng 30 fps. Có `SetWallpaperReceiver` để chọn
  wallpaper bằng adb (`setWallpaperComponent`, cần `SET_WALLPAPER_COMPONENT`), vì bản car không có
  màn chọn wallpaper.
* `KaneCameraTestFrameworkOverlay`: RRO đặt `config_enableWallpaperService=true`. **Phải ký
  platform**, vì resource này không thuộc `<overlayable>`.
* `KaneTouchTest`: lưới 4×3 nút, mỗi lần bấm ghi log `TouchTest: CLICK button N`.
* File privapp-permissions và `cameratest.mk` (gồm `PRODUCT_PACKAGES`).

### 3.5 Property test
`persist.debug.camera.emulator_test` (=1 nghĩa là đang test emulator). Chọn namespace `debug`
để app nhiều khả năng đọc được mà không phải thêm sepolicy. App và SystemUI đọc lại property
mỗi lần bật camera.

---

## 4. Các bẫy đã gặp (rất quan trọng)

1. **Không đăng ký organizer thứ hai** cho `FEATURE_DEFAULT_TASK_CONTAINER`. WM sẽ âm thầm thay
   organizer cũ và làm hỏng Shell (mục 2.1).
2. **Shell main thread khác SysUI main thread trên car.** Phải dùng
   `organizer.getExecutor()` cho mọi thao tác với organizer và leash.
3. **`hide()` system bars chỉ có tác dụng với window hệ thống đang focus.** Window camera không
   được có `FLAG_NOT_FOCUSABLE`; popup thì phải có.
4. **HSUM bật trên emulator car 14.** SystemUI và app camera chạy ở user 0, còn current user là
   10. Vì vậy:
   * window phải có `SYSTEM_FLAG_SHOW_FOR_ALL_USERS` (WM chỉ chấp nhận khi chủ window có
     `INTERNAL_SYSTEM_WINDOW`);
   * broadcast dùng `--user 0`;
   * mở activity bằng `startActivityAsUser(..., UserHandle.CURRENT)`;
   * đặt wallpaper bằng `--user current`.
5. **Bản car tắt wallpaper service** (`car_product` overlay
   `config_enableWallpaperService=false`). Muốn dùng live wallpaper làm plane giả thì phải bật
   lại bằng RRO ký platform.
6. **Comment XML không được chứa `--`** (ví dụ `--user`). Nếu có, aapt sẽ báo "not well-formed".
7. **App camera bị đóng băng rồi ANR rồi bị kill**, đã gặp thật khi test. App được khởi động
   bằng broadcast; khi `onReceive` xong thì tiến trình bị coi là cached, và cached apps freezer
   của Android 14 đóng băng nó sau khoảng 10 giây. Touch vào window camera không được xử lý
   (log ANR ghi `Frozen: true`, `Input dispatching timed out ... CameraTestWindow`), và khoảng
   30 giây sau app bị kill. **Cách sửa:** `android:persistent="true"`.
8. **Khi SystemUI restart**, app sẽ lộ qua lỗ khoảng 1–3 giây cho tới khi app camera reconnect.
   Đây là giới hạn đã biết của phương án B.
9. **Window hệ thống nằm dưới window camera và ngoài TDA** (`TYPE_APPLICATION_OVERLAY`, toast)
   vẫn lộ qua lỗ. Nút 1 dùng để minh họa giới hạn này.
10. **Push file từ Windows lên emulator** sẽ làm quyền file sai. Phải `chown root:root`,
    `chmod 755` cho thư mục, `644` cho file, rồi `restorecon -R`.
11. **Nếu product có SystemUI tùy biến** (từng có `MyCarSystemUI` thay thế `CarSystemUI`):
    * Bỏ nó khỏi `PRODUCT_PACKAGES`, chạy `m installclean`, rồi build lại.
    * Hoặc, trên emulator đang chạy, `rm` thư mục `/system_ext/priv-app/MyCarSystemUI` cùng các
      RRO `/product/overlay/MyCarSystemUI*.apk`, rồi push `CarSystemUI`.
    * Không dùng `adb sync`, vì nó đẩy luôn APK cũ còn trong `out/`.
12. `PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := strict`: module mới phải nằm ở `system_ext`
    hoặc `product`, không được ở `system`.

---

## 5. Danh sách file

### Sửa file có sẵn
| File | Thay đổi |
|---|---|
| `frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/RootTaskDisplayAreaOrganizer.java` | Thêm `setDisplayAreaVisibility()` ngay sau `setPosition()` |
| `packages/apps/Car/SystemUI/Android.bp` | Thêm `"car-camera-app-area-aidl"` vào `static_libs` của `CarSystemUI-core` |
| `packages/apps/Car/SystemUI/AndroidManifest.xml` | Thêm `<permission>` và `<service>` |
| `packages/apps/Car/SystemUI/src/com/android/systemui/CarSysUIComponent.java` | Thêm `CameraAppAreaModule.class` vào `modules` |

### File mới
```
packages/apps/Car/SystemUI/libs/camera-app-area/Android.bp
packages/apps/Car/SystemUI/libs/camera-app-area/src/com/android/systemui/car/cameraarea/ICameraAppArea.aidl
packages/apps/Car/SystemUI/libs/camera-app-area/src/com/android/systemui/car/cameraarea/CameraAppAreaContract.java
packages/apps/Car/SystemUI/src/com/android/systemui/car/cameraarea/CameraAppAreaController.java
packages/apps/Car/SystemUI/src/com/android/systemui/car/cameraarea/CameraAppAreaService.java
packages/apps/Car/SystemUI/src/com/android/systemui/car/cameraarea/CameraAppAreaModule.java
vendor/kane/cameratest/cameratest.mk
vendor/kane/cameratest/README.md
vendor/kane/cameratest/privapp/{Android.bp, privapp-permissions-kane-cameratest.xml}
vendor/kane/cameratest/CameraTestApp/{Android.bp, AndroidManifest.xml}
vendor/kane/cameratest/CameraTestApp/res/layout/{camera_window.xml, activity_transparency_test.xml}
vendor/kane/cameratest/CameraTestApp/res/values/{strings.xml, styles.xml}
vendor/kane/cameratest/CameraTestApp/src/com/kane/cameratest/
    AppAreaClient.java  CameraRootLayout.java  CameraSession.java  CameraTestConfig.java
    CameraTriggerReceiver.java  CameraWindow.java  GuidelineView.java  SeqLog.java
    TaskStackDumper.java  TestPopups.java  TransparencyTestActivity.java
    subcore/SubCoreCameraClient.java  subcore/RealSubCoreCameraClient.java
    subcore/emulator/FakeSubCoreCameraClient.java
vendor/kane/cameratest/TestPatternWallpaper/{Android.bp, AndroidManifest.xml, res/xml/wallpaper.xml,
    res/values/strings.xml, src/com/kane/testpattern/{TestPatternWallpaperService,SetWallpaperReceiver}.java}
vendor/kane/cameratest/TouchTestApp/{Android.bp, AndroidManifest.xml, src/com/kane/touchtest/TouchTestActivity.java}
vendor/kane/cameratest/overlay/EmulatorWallpaperOverlay/{Android.bp, AndroidManifest.xml, res/values/config.xml}
```
Nếu công ty dùng tên vendor khác `kane`, đổi đường dẫn và package `com.kane.*` cho phù hợp.

### Quyền của app camera
| Quyền | Dùng để làm gì |
|---|---|
| `CONTROL_CAMERA_APP_AREA` (signature) | Bind service của SystemUI |
| `INTERNAL_SYSTEM_WINDOW` | Window `TYPE_SYSTEM_ALERT` / `TYPE_SYSTEM_ERROR` và `SYSTEM_FLAG_SHOW_FOR_ALL_USERS` |
| `SYSTEM_ALERT_WINDOW` | Popup dưới |
| `MANAGE_ACTIVITY_TASKS`, `DUMP` | Nút 4 |
| `INTERACT_ACROSS_USERS`, `INTERACT_ACROSS_USERS_FULL`, `START_ACTIVITIES_FROM_BACKGROUND` | Nút 3 |

Allowlist privapp cho `com.kane.cameratest` gồm `DUMP`, `INTERACT_ACROSS_USERS`,
`START_ACTIVITIES_FROM_BACKGROUND`; cho `com.kane.testpattern` gồm `SET_WALLPAPER_COMPONENT`.

---

## 6. Build, cài đặt và test

### 6.1 Thêm vào product
Thêm vào makefile của product (ví dụ `vendor/kane/products/kane_car.mk`, được include từ
`device/generic/car/sdk_car_x86_64.mk`):
```make
$(call inherit-product, vendor/kane/cameratest/cameratest.mk)
```

### 6.2 Build
```bash
source build/envsetup.sh
lunch sdk_car_x86_64-userdebug
m installclean     # nếu vừa bỏ một SystemUI tùy biến
m -j$(nproc)
```
Build từng module (WMShell được build tĩnh vào SystemUI):
```bash
m CarSystemUI KaneCameraTest KaneTestPatternWallpaper KaneTouchTest KaneCameraTestFrameworkOverlay privapp-permissions-kane-cameratest.xml
```

### 6.3 Push lên emulator đang chạy (emulator chạy với `-writable-system`)
Gom file trong máy build:
```bash
cd $ANDROID_PRODUCT_OUT && tar -czf ~/push_modules.tar.gz system_ext/priv-app/CarSystemUI system_ext/priv-app/KaneCameraTest system_ext/priv-app/KaneTestPatternWallpaper system_ext/app/KaneTouchTest system_ext/etc/permissions/privapp-permissions-kane-cameratest.xml product/overlay/KaneCameraTestFrameworkOverlay.apk
```
Trên máy có adb:
```bash
adb root
adb remount
adb push <dir>/system_ext/priv-app/CarSystemUI /system_ext/priv-app/
adb push <dir>/system_ext/priv-app/KaneCameraTest /system_ext/priv-app/
adb push <dir>/system_ext/priv-app/KaneTestPatternWallpaper /system_ext/priv-app/
adb push <dir>/system_ext/app/KaneTouchTest /system_ext/app/
adb push <dir>/system_ext/etc/permissions/privapp-permissions-kane-cameratest.xml /system_ext/etc/permissions/
adb push <dir>/product/overlay/KaneCameraTestFrameworkOverlay.apk /product/overlay/
adb shell 'for p in /system_ext/priv-app/CarSystemUI /system_ext/priv-app/KaneCameraTest /system_ext/priv-app/KaneTestPatternWallpaper /system_ext/app/KaneTouchTest /system_ext/etc/permissions/privapp-permissions-kane-cameratest.xml /product/overlay/KaneCameraTestFrameworkOverlay.apk; do chown -R root:root $p; find $p -type d -exec chmod 755 {} +; find $p -type f -exec chmod 644 {} +; restorecon -R $p; done'
adb reboot
```
Nếu chạy trong PowerShell, đặt lệnh `adb shell` trong dấu nháy kép và đổi `$p` thành `` `$p ``.

Kiểm tra sau khi boot:
```bash
adb shell pm path com.android.systemui                 # → /system_ext/priv-app/CarSystemUI/CarSystemUI.apk
adb shell pm list packages | grep kane                 # 3 app + overlay
adb shell cmd overlay list android | grep -i kane      # [x]
adb shell dumpsys activity processes | grep -i kane.cameratest   # có PERS
```

### 6.4 Chạy test
```bash
adb shell setprop persist.debug.camera.emulator_test 1
adb shell am broadcast --user current -a com.kane.testpattern.action.SET -p com.kane.testpattern
adb logcat -s CamSeq:I FakeSubCore:I CameraAppArea:I AppAreaClient:I CameraWindow:I
adb shell am broadcast --user 0 -a com.kane.cameratest.action.SHOW -p com.kane.cameratest
adb shell am broadcast --user 0 -a com.kane.cameratest.action.HIDE -p com.kane.cameratest
```
Log đã đo được trên emulator thật khi bật camera (đúng như thiết kế):
```
CamSeq        : === CAMERA ON #1 emulatorTest=true subCore=FakeSubCoreCameraClient
AppAreaClient : Connected to SystemUI (reconnected=false)
CamSeq        : 1. hideApps (deferred, applied with the window's first frame)
CameraAppArea : hideApps display=0 deferApply=true
CamSeq        : 2. add camera window (hideTx applied in the same frame)
CamSeq        : 3. showCamera rect=[0,0][1408,792]
FakeSubCore   : #1 uptime=...ms wall=... showCamera rect=[0,0][1408,792] size=1408x792
```
Các kịch bản test chi tiết (dumpsys window containers, SurfaceFlinger, touch, đổi
`wm size`/`density`, guard `am start`, bật/tắt 50 lần, kill SystemUI, kill app camera) nằm trong
`vendor/kane/cameratest/README.md`, có trong `HANDOFF_2_ma_nguon.md`.

### 6.5 Tình trạng hiện tại
* Đã chạy được: SystemUI ẩn TDA đúng thứ tự, failsafe khi app camera chết hoạt động.
* Đã sửa: lỗi app bị đóng băng (bẫy 4.7) bằng `persistent`; **chưa test lại sau khi sửa**.
* Còn là stub hoặc chưa làm:
  * `RealSubCoreCameraClient`: IPC thật;
  * tín hiệu số lùi thật (VHAL `GEAR_SELECTION`);
  * webcam qua Camera2 (tùy chọn);
  * sepolicy cho property nếu bị chặn.
* Emulator không test được: blend plane thật (ARGB/XRGB, premultiplied, zpos), HWC/DPU của
  vendor, độ trễ camera, early RVC lúc boot, đổi zpos ở chế độ SAFETY.
