package com.kane.testpattern;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fake camera plane for the emulator: an animated color grid, a sweeping bar and a running
 * timestamp/frame counter, so it is obvious that the image under the hole is live.
 */
public class TestPatternWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new PatternEngine();
    }

    private final class PatternEngine extends Engine {
        private static final long FRAME_MS = 33;
        private static final int COLS = 8;
        private static final int ROWS = 6;

        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private final Paint mFill = new Paint();
        private final Paint mLine = new Paint();
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mTextBg = new Paint();
        private final SimpleDateFormat mTime = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        private final float[] mHsv = {0f, 0.8f, 0.9f};
        private final long mStart = SystemClock.uptimeMillis();
        private long mFrame;
        private boolean mVisible;

        private final Runnable mDrawRunnable = this::drawFrame;

        PatternEngine() {
            mLine.setColor(Color.WHITE);
            mLine.setStrokeWidth(2f);
            mText.setColor(Color.WHITE);
            mText.setTypeface(Typeface.MONOSPACE);
            mText.setTextAlign(Paint.Align.CENTER);
            mTextBg.setColor(0xB0000000);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
            mHandler.removeCallbacks(mDrawRunnable);
            if (visible) {
                drawFrame();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            mVisible = false;
            mHandler.removeCallbacks(mDrawRunnable);
        }

        @Override
        public void onDestroy() {
            mHandler.removeCallbacks(mDrawRunnable);
        }

        private void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
                if (c != null) {
                    draw(c);
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }
            mHandler.removeCallbacks(mDrawRunnable);
            if (mVisible) {
                mHandler.postDelayed(mDrawRunnable, FRAME_MS);
            }
        }

        private void draw(Canvas c) {
            final int w = c.getWidth();
            final int h = c.getHeight();
            final long t = SystemClock.uptimeMillis() - mStart;
            mFrame++;

            // Color grid, hues rotating over time.
            final float cw = w / (float) COLS;
            final float ch = h / (float) ROWS;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    mHsv[0] = ((col * 45 + row * 20) + t / 20f) % 360f;
                    mFill.setColor(Color.HSVToColor(mHsv));
                    c.drawRect(col * cw, row * ch, (col + 1) * cw, (row + 1) * ch, mFill);
                }
            }
            for (int col = 1; col < COLS; col++) {
                c.drawLine(col * cw, 0, col * cw, h, mLine);
            }
            for (int row = 1; row < ROWS; row++) {
                c.drawLine(0, row * ch, w, row * ch, mLine);
            }

            // Sweeping bar: motion is visible even in a screenshot pair.
            final float barX = (t / 4f) % w;
            mFill.setColor(0xCCFFFFFF);
            c.drawRect(barX, 0, barX + w / 40f, h, mFill);

            // Timestamp and frame counter.
            mText.setTextSize(h / 12f);
            final String l1 = "FAKE CAMERA PLANE";
            final String l2 = mTime.format(new Date());
            final String l3 = "frame " + mFrame + "  " + w + "x" + h;
            final float lh = mText.getTextSize() * 1.2f;
            final float top = h / 2f - lh * 1.5f;
            c.drawRect(w * 0.2f, top - lh * 0.2f, w * 0.8f, top + lh * 3f, mTextBg);
            c.drawText(l1, w / 2f, top + lh * 0.8f, mText);
            c.drawText(l2, w / 2f, top + lh * 1.8f, mText);
            c.drawText(l3, w / 2f, top + lh * 2.8f, mText);
        }
    }
}
