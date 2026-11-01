package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Small local seconds dial beside the Home clock; redraws only while Home is visible. */
public final class ItachiSecondsDialView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface digitTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private boolean active;
    private long now = System.currentTimeMillis();
    private float density;

    public ItachiSecondsDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
    }

    void start() {
        if (active) return;
        active = true;
        tick();
    }

    void stop() {
        active = false;
        handler.removeCallbacks(tick);
    }

    private void tick() {
        if (!active) return;
        now = System.currentTimeMillis();
        invalidate();
        handler.postDelayed(tick, 100L);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
        float radius = Math.min(cx, cy) - 2f * density;
        if (radius <= 0f) return;
        float seconds = (now / 1_000L) % 60L + (now % 1_000L) / 1_000f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(0x9953aaff);
        canvas.drawCircle(cx, cy, radius, paint);
        for (int i = 0; i < 60; i++) {
            float angle = (float) Math.toRadians(i * 6f - 90f);
            boolean major = i % 5 == 0;
            float inner = radius - (major ? 5.5f : 2.8f) * density;
            paint.setStrokeWidth((major ? 1.4f : 0.8f) * density);
            paint.setColor(i <= (int) seconds ? 0xff8bc8ff : 0x665276a0);
            canvas.drawLine(cx + (float) Math.cos(angle) * inner,
                    cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * (radius - density),
                    cy + (float) Math.sin(angle) * (radius - density), paint);
        }

        float handAngle = (float) Math.toRadians(seconds * 6f - 90f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.6f * density);
        paint.setColor(0xff72baff);
        canvas.drawLine(cx, cy, cx + (float) Math.cos(handAngle) * radius * 0.62f,
                cy + (float) Math.sin(handAngle) * radius * 0.62f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffa6d7ff);
        canvas.drawCircle(cx, cy, 2.1f * density, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(9f * density);
        paint.setTypeface(digitTypeface);
        paint.setColor(0xffd9eeff);
        String label = String.format(Locale.US, "%02d", (int) seconds % 60);
        canvas.drawText(label, cx, cy + 3f * density, paint);
    }
}
