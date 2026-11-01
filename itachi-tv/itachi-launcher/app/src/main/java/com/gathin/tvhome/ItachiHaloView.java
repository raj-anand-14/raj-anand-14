package com.gathin.tvhome;

import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;

/**
 * The ring behind each app mark in the Itachi dock. At rest a faint silver ring; focused, it ignites into a
 * red Sharingan ring with a soft chakra glow and three tomoe orbiting the mark.
 */
public final class ItachiHaloView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tomoe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path comma = new Path();
    private float lit;          // 0 at rest, 1 focused
    private float markSize;     // the larger side of the mark, in px
    private float angle;
    private long last;
    private ValueAnimator anim;
    private final float density;

    public ItachiHaloView(Context c) { this(c, null); }

    public ItachiHaloView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        inner.setStyle(Paint.Style.STROKE);
        glow.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{Color.argb(70, 230, 20, 32), Color.argb(30, 160, 0, 16), Color.TRANSPARENT},
                new float[]{.35f, .7f, 1f}, Shader.TileMode.CLAMP));
        tomoe.setColor(Color.rgb(232, 22, 34));
        // A unit tomoe: head at the origin, tail sweeping back along the ring.
        comma.addCircle(0f, 0f, 1f, Path.Direction.CW);
        Path tail = new Path();
        tail.moveTo(-1f, 0f);
        tail.quadTo(-.7f, 2.3f, 2.1f, 1.6f);
        tail.quadTo(.5f, 1.15f, 1f, 0f);
        tail.close();
        comma.op(tail, Path.Op.UNION);
        setWillNotDraw(false);
    }

    /** The mark's larger side, so the ring wraps it with the same margin for every app. */
    void setMarkSize(float px) {
        markSize = px;
        invalidate();
    }

    void setLit(boolean on, boolean animate) {
        if (anim != null) anim.cancel();
        float to = on ? 1f : 0f;
        if (!animate) {
            lit = to;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(lit, to);
        anim.setDuration(on ? 320L : 200L);
        anim.setInterpolator(new DecelerateInterpolator(1.4f));
        anim.addUpdateListener(v -> {
            lit = (float) v.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (markSize <= 0f) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : Math.min(.05f, (now - last) / 1000f);
        last = now;
        angle = (angle + dt * (24f + 60f * lit)) % 360f;

        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        // One ring size for every app, so each mark sits in the same highlight.
        float r = 31f * density;

        if (lit > .01f) {
            c.save();
            c.translate(cx, cy);
            float g = r * (1.18f + .08f * lit);
            c.scale(g, g);
            glow.setAlpha((int) (255 * lit));
            c.drawCircle(0f, 0f, 1f, glow);
            c.restore();
        }

        // The ring: faint silver at rest, Sharingan red when focused.
        int red = Color.rgb(226, 18, 30);
        // Clean marks at rest, like a plain row of logos; the ring appears only on the focused app.
        if (lit <= .01f) { last = 0; return; }
        ring.setColor(blend(Color.argb(0, 226, 18, 30), red, lit));
        ring.setStrokeWidth((1f + 1.2f * lit) * density);
        c.drawCircle(cx, cy, r, ring);
        if (lit > .01f) {
            inner.setColor(Color.argb((int) (70 * lit), 255, 60, 70));
            inner.setStrokeWidth(.8f * density);
            c.drawCircle(cx, cy, r * .84f, inner);

            // Three tomoe riding the ring.
            float tr = 2.6f * density * (.6f + .4f * lit);
            tomoe.setAlpha((int) (255 * lit));
            for (int i = 0; i < 3; i++) {
                c.save();
                c.rotate(angle + i * 120f, cx, cy);
                c.translate(cx, cy - r);
                c.scale(tr, tr);
                c.drawPath(comma, tomoe);
                c.restore();
            }
        }
        if (lit > .01f && isShown()) postInvalidateOnAnimation();
        else last = 0;
    }

    private static int blend(int a, int b, float t) {
        return Color.argb(
                (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
