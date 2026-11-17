package com.kane.cameratest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Rear-camera parking guideline drawn over the (transparent) camera hole. No background. */
public class GuidelineView extends View {
    private static final int[] COLORS = {0xFF00E676, 0xFFFFEA00, 0xFFFF1744};

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GuidelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(8f * getResources().getDisplayMetrics().density);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float w = getWidth();
        final float h = getHeight();
        // Trapezoid from the bottom (near) to the horizon (far), split into 3 distance bands.
        final float nearY = h * 0.95f;
        final float farY = h * 0.45f;
        final float nearHalf = w * 0.30f;
        final float farHalf = w * 0.14f;
        final float cx = w * 0.5f;
        for (int i = 0; i < COLORS.length; i++) {
            final float t0 = i / 3f;
            final float t1 = (i + 1) / 3f;
            final float y0 = nearY + (farY - nearY) * t0;
            final float y1 = nearY + (farY - nearY) * t1;
            final float half0 = nearHalf + (farHalf - nearHalf) * t0;
            final float half1 = nearHalf + (farHalf - nearHalf) * t1;
            mPaint.setColor(COLORS[i]);
            canvas.drawLine(cx - half0, y0, cx - half1, y1, mPaint);
            canvas.drawLine(cx + half0, y0, cx + half1, y1, mPaint);
            canvas.drawLine(cx - half1, y1, cx - half1 * 0.6f, y1, mPaint);
            canvas.drawLine(cx + half1, y1, cx + half1 * 0.6f, y1, mPaint);
        }
    }
}
