package com.itachi.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** The animated eye: idle drift, listening pulse with your voice, fast spin while Claude thinks. */
public final class SharinganView extends View {

    public enum Mode { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tomoe = new Path();

    private Mode mode = Mode.IDLE;
    private float level;        // smoothed voice level 0..1
    private float target;       // latest voice level 0..1
    private float angle;        // tomoe rotation, degrees
    private float speed = 20f;  // current degrees per second, eased toward the mode's speed
    private long last;
    private float lastSize = -1;

    public SharinganView(Context c) { this(c, null); }

    public SharinganView(Context c, AttributeSet a) {
        super(c, a);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Color.rgb(20, 0, 3));
    }

    public void setMode(Mode m) {
        mode = m;
        if (m != Mode.LISTENING) target = 0;
        invalidate();
    }

    /** RMS from the recognizer, in dB, roughly -2..10. */
    public void setRms(float rmsDb) {
        target = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
    }

    private float modeSpeed() {
        switch (mode) {
            case LISTENING: return 70f + 260f * level;
            case THINKING: return 620f;
            case SPEAKING: return 140f;
            case ERROR: return 8f;
            default: return 22f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        level += (target - level) * Math.min(1f, dt * 10f);
        speed += (modeSpeed() - speed) * Math.min(1f, dt * 4f);
        angle = (angle + speed * dt) % 360f;

        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float max = Math.min(w, h) / 2f;
        float breathe = (float) Math.sin(now / 600.0);
        float pulse;
        switch (mode) {
            case LISTENING: pulse = 1f + 0.10f * level; break;
            case SPEAKING: pulse = 1f + 0.025f * (float) Math.sin(now / 140.0); break;
            case THINKING: pulse = 0.96f; break;
            default: pulse = 1f + 0.012f * breathe;
        }
        float r = max * 0.62f;
        if (lastSize != max) {
            lastSize = max;
            glow.setShader(new RadialGradient(cx, cy, max, new int[] {
                    Color.rgb(230, 20, 30), Color.argb(90, 160, 0, 10), Color.TRANSPARENT },
                    new float[] { 0.45f, 0.75f, 1f }, Shader.TileMode.CLAMP));
            iris.setShader(new RadialGradient(cx, cy, r, new int[] {
                    Color.rgb(255, 46, 52), Color.rgb(196, 8, 18), Color.rgb(110, 0, 8) },
                    new float[] { 0f, 0.7f, 1f }, Shader.TileMode.CLAMP));
        }

        // Glow behind the eye.
        float glowStrength = mode == Mode.LISTENING ? 0.45f + 0.55f * level
                : mode == Mode.THINKING ? 0.7f + 0.3f * Math.abs(breathe)
                : mode == Mode.SPEAKING ? 0.6f : mode == Mode.ERROR ? 0.15f : 0.3f + 0.1f * breathe;
        glow.setAlpha((int) (150 * glowStrength));
        c.drawCircle(cx, cy, max, glow);

        c.save();
        c.scale(pulse, pulse, cx, cy);

        // Iris.
        iris.setAlpha(mode == Mode.ERROR ? 110 : 255);
        c.drawCircle(cx, cy, r, iris);
        stroke.setStrokeWidth(r * 0.07f);
        c.drawCircle(cx, cy, r, stroke);
        stroke.setStrokeWidth(r * 0.03f);
        c.drawCircle(cx, cy, r * 0.56f, stroke);

        // Pupil.
        fill.setColor(Color.BLACK);
        c.drawCircle(cx, cy, r * (mode == Mode.LISTENING ? 0.17f + 0.05f * level : 0.18f), fill);

        // Three tomoe, riding the inner ring.
        float tr = r * 0.12f;
        for (int i = 0; i < 3; i++) {
            c.save();
            c.rotate(angle + i * 120f, cx, cy);
            float tx = cx, ty = cy - r * 0.56f;
            c.drawCircle(tx, ty, tr, fill);
            tomoe.reset();
            // Comma tail, sweeping backwards along the ring.
            tomoe.moveTo(tx - tr, ty);
            tomoe.quadTo(tx - tr * 0.6f, ty + tr * 2.2f, tx + tr * 1.9f, ty + tr * 1.7f);
            tomoe.quadTo(tx + tr * 0.4f, ty + tr * 1.2f, tx + tr, ty);
            tomoe.close();
            c.drawPath(tomoe, fill);
            c.restore();
        }
        c.restore();

        if (isShown()) postInvalidateOnAnimation();
    }
}
