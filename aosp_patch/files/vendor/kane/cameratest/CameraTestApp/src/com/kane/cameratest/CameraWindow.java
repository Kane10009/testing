package com.kane.cameratest;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * The hole-punch camera window: full screen, translucent, nothing drawn over the hole (alpha 0)
 * except the guideline and buttons. Hides the status/navigation bars like a fullscreen video:
 * as a focused system window it is its own insets control target (car's BarControlPolicy only
 * takes over for application windows).
 */
final class CameraWindow {
    private static final String TAG = "CameraWindow";
    /** Layer 12: above TYPE_APPLICATION_OVERLAY (11), below IME (13) and the bars (15/24). */
    @SuppressWarnings("deprecation")
    static final int WINDOW_TYPE = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    /** Safety net in case the frame carrying the "show apps" transaction never commits. */
    private static final long REMOVE_TIMEOUT_MS = 500;

    interface Callbacks {
        /** First frame with the hole is on screen: time to tell the sub-core. */
        void onFirstFrameCommitted(Rect holeRect);
        void onHoleRectChanged(Rect holeRect);
        void onPopupBelowClicked();
        void onPopupAboveClicked();
        void onOpenActivityClicked();
        void onDumpStackClicked();
    }

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Callbacks mCallbacks;
    private final boolean mShowWallpaper;
    private final Rect mHoleRect = new Rect();
    private final int[] mTmpLocation = new int[2];

    private ViewGroup mRoot;
    private View mHole;
    private TextView mTouchCounter;
    private View mStackPanel;
    private TextView mStackText;
    private int mHoleTouches;
    private boolean mFirstFrameCommitted;
    private boolean mRemoved;

    private final ViewTreeObserver.OnGlobalLayoutListener mLayoutListener = this::updateHoleRect;

    CameraWindow(Context appContext, int displayId, boolean showWallpaper, Callbacks callbacks) {
        final Display display = appContext.getSystemService(DisplayManager.class)
                .getDisplay(displayId);
        final Context windowContext = appContext.createDisplayContext(display)
                .createWindowContext(WINDOW_TYPE, null);
        mContext = new ContextThemeWrapper(windowContext, R.style.CameraWindowTheme);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mShowWallpaper = showWallpaper;
        mCallbacks = callbacks;
    }

    /**
     * Adds the window. {@code hideAppsTx} (from SystemUI, may be null) is applied in the same
     * frame as the window's first frame, so the apps disappear exactly when the hole appears.
     */
    void show(@Nullable SurfaceControl.Transaction hideAppsTx) {
        mRoot = (ViewGroup) LayoutInflater.from(mContext).inflate(R.layout.camera_window, null);
        mHole = mRoot.findViewById(R.id.camera_hole);
        mTouchCounter = mRoot.findViewById(R.id.hole_touch_counter);
        mStackPanel = mRoot.findViewById(R.id.stack_panel);
        mStackText = mRoot.findViewById(R.id.stack_text);
        mTouchCounter.setText(mContext.getString(R.string.hole_touch_counter, 0));

        // The hole swallows every touch: nothing may reach the hidden apps.
        mHole.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mHoleTouches++;
                mTouchCounter.setText(mContext.getString(R.string.hole_touch_counter,
                        mHoleTouches));
                Log.i(TAG, "Hole touch #" + mHoleTouches + " at " + (int) ev.getRawX() + ","
                        + (int) ev.getRawY() + " swallowed");
            }
            return true;
        });
        mRoot.findViewById(R.id.btn_popup_below).setOnClickListener(
                v -> mCallbacks.onPopupBelowClicked());
        mRoot.findViewById(R.id.btn_popup_above).setOnClickListener(
                v -> mCallbacks.onPopupAboveClicked());
        mRoot.findViewById(R.id.btn_open_activity).setOnClickListener(
                v -> mCallbacks.onOpenActivityClicked());
        mRoot.findViewById(R.id.btn_dump_stack).setOnClickListener(
                v -> mCallbacks.onDumpStackClicked());
        mStackText.setOnClickListener(v -> mStackPanel.setVisibility(View.GONE));

        mRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                // Runs in the first traversal, before the first draw.
                if (hideAppsTx != null) {
                    v.getRootSurfaceControl().applyTransactionOnDraw(hideAppsTx);
                }
                v.getViewTreeObserver().registerFrameCommitCallback(() -> {
                    mFirstFrameCommitted = true;
                    updateHoleRect();
                    mCallbacks.onFirstFrameCommitted(new Rect(mHoleRect));
                });
            }

            @Override
            public void onViewDetachedFromWindow(View v) {}
        });

        mWindowManager.addView(mRoot, createLayoutParams());

        final WindowInsetsController insets = mRoot.getWindowInsetsController();
        if (insets != null) {
            insets.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insets.hide(WindowInsets.Type.systemBars());
        }
        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
    }

    private WindowManager.LayoutParams createLayoutParams() {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (mShowWallpaper) {
            // Emulator test mode: the live wallpaper stands in for the camera plane.
            flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        }
        // Focusable on purpose (no FLAG_NOT_FOCUSABLE): only the focused window may hide the bars.
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WINDOW_TYPE, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // Lay out under the bars whether they are shown or not: the hole rect never jumps when
        // the bars slide in/out (e.g. transient swipe).
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        // CarSystemUI and this app run as the headless system user; show for the current user.
        lp.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        lp.windowAnimations = 0;
        lp.setTitle("CameraTestWindow");
        return lp;
    }

    private void updateHoleRect() {
        if (mHole == null || !mHole.isAttachedToWindow()) {
            return;
        }
        mHole.getLocationOnScreen(mTmpLocation);
        final Rect r = new Rect(mTmpLocation[0], mTmpLocation[1],
                mTmpLocation[0] + mHole.getWidth(), mTmpLocation[1] + mHole.getHeight());
        if (r.equals(mHoleRect)) {
            return;
        }
        mHoleRect.set(r);
        if (mFirstFrameCommitted) {
            mCallbacks.onHoleRectChanged(new Rect(r));
        }
    }

    void showStackDump(String text) {
        if (mRoot == null) {
            return;
        }
        mStackText.setText(text);
        mStackPanel.setVisibility(View.VISIBLE);
    }

    /**
     * Removes the window. If {@code showAppsTx} is given, the apps reappear in the same frame in
     * which the camera UI disappears (the window is still there but draws nothing), and the
     * window is removed once that frame has been committed.
     */
    void remove(@Nullable SurfaceControl.Transaction showAppsTx, Runnable onRemoved) {
        if (mRoot == null || mRemoved) {
            if (showAppsTx != null) {
                showAppsTx.apply();
            }
            onRemoved.run();
            return;
        }
        mRoot.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
        if (showAppsTx == null || !mRoot.isAttachedToWindow()) {
            if (showAppsTx != null) {
                showAppsTx.apply();
            }
            removeNow(onRemoved);
            return;
        }
        // Children only: hiding the root would relayout the window and hide its surface in a
        // different transaction than the one showing the apps.
        for (int i = 0; i < mRoot.getChildCount(); i++) {
            mRoot.getChildAt(i).setVisibility(View.INVISIBLE);
        }
        final Runnable remove = () -> removeNow(onRemoved);
        showAppsTx.addTransactionCommittedListener(mContext.getMainExecutor(), remove::run);
        mRoot.getRootSurfaceControl().applyTransactionOnDraw(showAppsTx);
        mRoot.invalidate();
        mRoot.postDelayed(remove, REMOVE_TIMEOUT_MS);
    }

    private void removeNow(Runnable onRemoved) {
        if (mRemoved) {
            return;
        }
        mRemoved = true;
        mWindowManager.removeViewImmediate(mRoot);
        onRemoved.run();
    }

    Rect getHoleRect() {
        return new Rect(mHoleRect);
    }
}
