package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Screen effects for the Itachi theme: light rain that leans toward the focused app, and a burst of crows
 * with a red flash when an app is opened.
 */
public final class ItachiFxView extends View {
    private static final int DROPS = 90;
    private static final int CROWS = 11;

    private final Random random = new Random();
    private final Paint rain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flash = new Paint();
    private final Path crow = new Path();
    private final float[] dx = new float[DROPS], dy = new float[DROPS], len = new float[DROPS],
            speed = new float[DROPS], alpha = new float[DROPS];
    private final float[] cx = new float[CROWS], cy = new float[CROWS], vx = new float[CROWS], vy = new float[CROWS],
            size = new float[CROWS], phase = new float[CROWS];
    private final float density;
    private boolean running, seeded;
    private float wind = .10f, windTarget = .10f;
    private float burst = -1f;      // seconds since the crows took off, or -1
    private long last;

    public ItachiFxView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        rain.setStrokeCap(Paint.Cap.ROUND);
        crowPaint.setColor(Color.rgb(6, 6, 10));
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);
    }

    void start() {
        if (running) return;
        running = true;
        last = 0;
        invalidate();
    }

    void stop() {
        running = false;
        burst = -1f;
    }

    /** Rain leans toward the focused app: x is 0..1 across the screen. */
    void lean(float x) {
        windTarget = .10f + (x - .5f) * .34f;
    }

    /** Crows scatter from (x, y) in screen pixels, with a red flash. */
    void crows(float x, float y) {
        for (int i = 0; i < CROWS; i++) {
            double a = Math.toRadians(-160 + random.nextInt(140));
            float v = (380 + random.nextInt(520)) * density / 2f;
            cx[i] = x + (random.nextFloat() - .5f) * 40 * density;
            cy[i] = y + (random.nextFloat() - .5f) * 20 * density;
            vx[i] = (float) Math.cos(a) * v;
            vy[i] = (float) Math.sin(a) * v;
            size[i] = (9 + random.nextInt(10)) * density;
            phase[i] = random.nextFloat() * 6.28f;
        }
        burst = 0f;
        last = 0;
        invalidate();
    }

    private void seed(int i, boolean anywhere) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        dx[i] = random.nextFloat() * w * 1.3f - w * .15f;
        dy[i] = anywhere ? random.nextFloat() * h : -random.nextFloat() * h * .2f;
        len[i] = (14 + random.nextInt(26)) * density;
        speed[i] = (520 + random.nextInt(520)) * density;
        alpha[i] = 18 + random.nextInt(44);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (!seeded) {
            for (int i = 0; i < DROPS; i++) seed(i, true);
            seeded = true;
        }
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : Math.min(.05f, (now - last) / 1000f);
        last = now;
        wind += (windTarget - wind) * Math.min(1f, dt * 2f);

        if (running) {
            rain.setStrokeWidth(1.1f * density);
            for (int i = 0; i < DROPS; i++) {
                dy[i] += speed[i] * dt;
                dx[i] += speed[i] * wind * dt;
                if (dy[i] - len[i] > h) seed(i, false);
                rain.setColor(Color.argb((int) alpha[i], 190, 210, 255));
                c.drawLine(dx[i], dy[i], dx[i] - len[i] * wind, dy[i] - len[i], rain);
            }
        }

        if (burst >= 0f) {
            burst += dt;
            float t = burst;
            if (t < .35f) {
                flash.setColor(Color.argb((int) (90 * (1f - t / .35f)), 200, 0, 14));
                c.drawRect(0, 0, w, h, flash);
            }
            int fade = (int) (255 * Math.max(0f, 1f - t / 1.1f));
            crowPaint.setAlpha(fade);
            for (int i = 0; i < CROWS; i++) {
                float x = cx[i] + vx[i] * t, y = cy[i] + vy[i] * t - 60 * density * t * t;
                float s = size[i] * (1f + t * 1.6f);
                float flap = (float) Math.sin(phase[i] + t * 22f);
                drawCrow(c, x, y, s, flap, vx[i] < 0);
            }
            if (t > 1.1f) burst = -1f;
        }

        if (running || burst >= 0f) postInvalidateOnAnimation();
        else last = 0;
    }

    /** A crow in flight: a small body with two wings that beat with flap (-1..1). */
    private void drawCrow(Canvas c, float x, float y, float s, float flap, boolean left) {
        crow.reset();
        float wingY = -flap * .9f;
        crow.moveTo(0f, 0f);
        crow.quadTo(-.6f, -.2f + wingY * .5f, -1.4f, wingY);
        crow.quadTo(-.8f, .05f, -.25f, .25f);
        crow.lineTo(.25f, .25f);
        crow.quadTo(.8f, .05f, 1.4f, wingY);
        crow.quadTo(.6f, -.2f + wingY * .5f, 0f, 0f);
        crow.close();
        crow.addCircle(left ? -.18f : .18f, -.02f, .17f, Path.Direction.CW);
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.drawPath(crow, crowPaint);
        c.restore();
    }
}
