package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Random;

/**
 * Living Mangekyo Sharingan eyes painted over the wallpaper's own eyes. Each eye is redrawn inside its eyelid
 * shape: shadowed sclera, a textured iris with Itachi's three curved blades, a moving gaze that follows the
 * focused app, small idle saccades, blinks with real lids, a twist on every focus change and a full spin on wake
 * or launch.
 */
public final class ItachiArtView extends View {
    // Wallpaper geometry, in pixels of the 1920 x 1080 wallpaper (it is shown centre-cropped on a 16:9 screen).
    private static final float WP_W = 1920f, WP_H = 1080f;

    private static final class Eye {
        final float ax, ay, bx, by, upx, upy, lox, loy, cx, cy, r, cut;
        final int scleraTop, scleraBottom, skin;
        // Screen-space values, set in onSizeChanged.
        final RectF bounds = new RectF();
        final Path almond = new Path(), outside = new Path(), lid = new Path(), skinPath = new Path();
        Paint sclera, shadow, cutPaint;
        float sAx, sAy, sBx, sBy, sUpx, sUpy, sLox, sLoy, sCx, sCy, sR, sCut;

        Eye(float ax, float ay, float bx, float by, float upx, float upy, float lox, float loy,
            float cx, float cy, float r, float cut, int scleraTop, int scleraBottom, int skin) {
            this.ax = ax; this.ay = ay; this.bx = bx; this.by = by; this.upx = upx; this.upy = upy;
            this.lox = lox; this.loy = loy; this.cx = cx; this.cy = cy; this.r = r; this.cut = cut;
            this.scleraTop = scleraTop; this.scleraBottom = scleraBottom; this.skin = skin;
        }
    }

    private final Eye[] eyes = {
            // Viewer's left eye.
            new Eye(616, 398, 712, 440, 664, 381, 660, 485, 683, 425, 29, -1,
                    Color.rgb(32, 42, 80), Color.rgb(56, 68, 110), Color.rgb(47, 31, 41)),
            // Viewer's right eye: a lock of hair covers it left of x = 949.
            new Eye(900, 404, 1047, 378, 1001, 316, 981, 438, 1000, 372, 42, 949,
                    Color.rgb(30, 38, 72), Color.rgb(52, 62, 102), Color.rgb(84, 21, 26)),
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iris = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fibres = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blades = new Path();
    private final Path fibrePath = new Path();
    private final RectF oval = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable blinkLater = this::blink;
    private final Runnable saccadeLater = this::saccade;

    private boolean active;
    private float gazeX, gazeY, targetX, targetY, jitterX, jitterY;
    private float lidOpen = 1f;
    private float angle = 18f, spin;   // blade rotation (degrees) and its spin speed (degrees per second)
    private float boost;               // extra glow after a wake or launch, decays to 0
    private long lastFrame;
    private ValueAnimator blinkAnimator;
    private float density;

    public ItachiArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);

        iris.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{Color.rgb(255, 58, 58), Color.rgb(214, 14, 26), Color.rgb(140, 0, 12), Color.rgb(70, 0, 6)},
                new float[]{0f, .45f, .82f, 1f}, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{Color.argb(255, 255, 24, 34), Color.argb(90, 200, 0, 20), Color.TRANSPARENT},
                new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
        fibres.setStyle(Paint.Style.STROKE);
        fibres.setStrokeWidth(.018f);
        fibres.setColor(Color.argb(95, 120, 0, 8));
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.rgb(24, 0, 4));
        lash.setStyle(Paint.Style.STROKE);
        lash.setStrokeCap(Paint.Cap.ROUND);
        lash.setColor(Color.argb(235, 5, 4, 10));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        // Itachi's Mangekyo: three slim curved blades around the pupil, in a unit circle.
        for (int i = 0; i < 3; i++) {
            Path b = new Path();
            b.moveTo(.05f, -.30f);
            b.cubicTo(.55f, -.62f, .95f, -.30f, .97f, .22f);
            b.cubicTo(.80f, -.05f, .55f, -.25f, .27f, -.05f);
            b.close();
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(i * 120f);
            b.transform(m);
            blades.addPath(b);
        }
        blades.addCircle(0f, 0f, .27f, Path.Direction.CW);
        for (int i = 0; i < 48; i++) {
            double a = Math.toRadians(i * 7.5);
            float r1 = .86f + .06f * (i % 3) / 2f;
            fibrePath.moveTo((float) Math.cos(a) * .36f, (float) Math.sin(a) * .36f);
            fibrePath.lineTo((float) Math.cos(a) * r1, (float) Math.sin(a) * r1);
        }
    }

    void start() {
        if (active) return;
        active = true;
        lastFrame = 0;
        scheduleBlink();
        scheduleSaccade();
        invalidate();
    }

    void stop() {
        active = false;
        handler.removeCallbacks(blinkLater);
        handler.removeCallbacks(saccadeLater);
        if (blinkAnimator != null) blinkAnimator.cancel();
        blinkAnimator = null;
        lidOpen = 1f;
        spin = 0f;
        boost = 0f;
    }

    /** Home returned after the TV or an app: the Mangekyo spins up and glows. */
    void wake() {
        if (!active) return;
        spin = Math.max(spin, 900f);
        boost = 1f;
        invalidate();
    }

    /** An app is opening from Home: a fast spin and a flare. */
    void activate() {
        spin = 1500f;
        boost = 1.6f;
        invalidate();
    }

    /** Looks toward the focused app; a small twist of the blades answers every move. */
    void lookAt(float screenX, float screenY) {
        targetX = clamp((screenX - .50f) / .42f, -1f, 1f);
        targetY = clamp((screenY - .40f) / .52f, -.9f, 1f);
        jitterX = jitterY = 0f;
        if (active) spin = Math.max(spin, 200f);
        invalidate();
    }

    private void scheduleBlink() {
        handler.removeCallbacks(blinkLater);
        if (active) handler.postDelayed(blinkLater, 3_800L + random.nextInt(5_200));
    }

    private void blink() {
        if (!active) return;
        // Now and then a double blink, as people do.
        final boolean twice = random.nextInt(5) == 0;
        blinkAnimator = twice ? ValueAnimator.ofFloat(1f, 0f, 1f, 0f, 1f) : ValueAnimator.ofFloat(1f, 0f, 1f);
        blinkAnimator.setDuration(twice ? 520L : 260L);
        blinkAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        blinkAnimator.addUpdateListener(a -> {
            lidOpen = (float) a.getAnimatedValue();
            invalidate();
        });
        blinkAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                lidOpen = 1f;
                scheduleBlink();
            }
        });
        blinkAnimator.start();
    }

    private void scheduleSaccade() {
        handler.removeCallbacks(saccadeLater);
        if (active) handler.postDelayed(saccadeLater, 1_400L + random.nextInt(2_600));
    }

    /** Tiny idle glances around the point of focus, so the eyes never look painted on. */
    private void saccade() {
        if (!active) return;
        jitterX = (random.nextFloat() - .5f) * .28f;
        jitterY = (random.nextFloat() - .5f) * .22f;
        invalidate();
        scheduleSaccade();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float sx = w / WP_W, sy = h / WP_H, s = Math.min(sx, sy);
        for (Eye e : eyes) {
            e.sAx = e.ax * sx; e.sAy = e.ay * sy; e.sBx = e.bx * sx; e.sBy = e.by * sy;
            e.sUpx = e.upx * sx; e.sUpy = e.upy * sy; e.sLox = e.lox * sx; e.sLoy = e.loy * sy;
            e.sCx = e.cx * sx; e.sCy = e.cy * sy; e.sR = e.r * s; e.sCut = e.cut < 0 ? -1 : e.cut * sx;
            float top = Math.min(Math.min(e.sAy, e.sBy), e.sUpy), bottom = Math.max(Math.max(e.sAy, e.sBy), e.sLoy);
            e.bounds.set(Math.min(e.sAx, e.sBx) - 4 * s, top - 4 * s, Math.max(e.sAx, e.sBx) + 4 * s, bottom + 4 * s);
            e.almond.reset();
            e.almond.moveTo(e.sAx, e.sAy);
            e.almond.quadTo(e.sUpx, e.sUpy, e.sBx, e.sBy);
            e.almond.quadTo(e.sLox, e.sLoy, e.sAx, e.sAy);
            e.almond.close();
            // Everything outside the eyelids, within the eye's layer: erased so only the eye itself remains.
            e.outside.set(e.almond);
            e.outside.setFillType(Path.FillType.INVERSE_WINDING);
            e.sclera = new Paint();
            e.sclera.setShader(new LinearGradient(0, e.bounds.top, 0, e.bounds.bottom,
                    e.scleraTop, e.scleraBottom, Shader.TileMode.CLAMP));
            e.shadow = new Paint();
            e.shadow.setShader(new LinearGradient(0, e.bounds.top, 0, e.bounds.top + e.bounds.height() * .58f,
                    Color.argb(175, 4, 4, 10), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            if (e.sCut >= 0) {
                e.cutPaint = new Paint();
                e.cutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                e.cutPaint.setShader(new LinearGradient(e.sCut - 2 * s, 0, e.sCut + 3 * s, 0,
                        Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        // Fast, slightly damped eye movement toward the target plus the idle glance.
        float k = Math.min(1f, dt * 14f);
        float tx = clamp(targetX + jitterX, -1f, 1f), ty = clamp(targetY + jitterY, -1f, 1f);
        gazeX += (tx - gazeX) * k;
        gazeY += (ty - gazeY) * k;
        angle = (angle + spin * dt) % 360f;
        spin *= (float) Math.exp(-dt * 2.6f);
        if (spin < 2f) spin = 0f;
        boost = Math.max(0f, boost - dt * 1.4f);

        for (Eye e : eyes) drawEye(canvas, e);

        boolean moving = Math.abs(tx - gazeX) > .002f || Math.abs(ty - gazeY) > .002f || spin > 0f || boost > 0f;
        if (active && moving) postInvalidateOnAnimation();
        else lastFrame = 0;
    }

    private void drawEye(Canvas c, Eye e) {
        float ix = e.sCx + gazeX * e.sR * .34f;
        float iy = e.sCy + gazeY * e.sR * .16f;
        float r = e.sR;

        // Red chakra glow around the eye, over the wallpaper.
        c.save();
        c.translate(ix, iy);
        float g = r * (2.0f + .5f * boost);
        c.scale(g, g);
        glow.setAlpha((int) Math.min(255, 60 + 80 * boost));
        c.drawCircle(0f, 0f, 1f, glow);
        c.restore();

        int layer = c.saveLayer(e.bounds, null);
        c.drawRect(e.bounds, e.sclera);

        // Iris, in unit space.
        c.save();
        c.translate(ix, iy);
        c.scale(r, r);
        c.drawCircle(0f, 0f, 1f, iris);
        c.drawPath(fibrePath, fibres);
        ring.setStrokeWidth(.07f);
        c.drawCircle(0f, 0f, .965f, ring);
        c.rotate(angle);
        fill.setColor(Color.rgb(6, 0, 3));
        c.drawPath(blades, fill);
        c.restore();

        // Wet highlight from the scene's light, upper left.
        fill.setColor(Color.argb(170, 255, 236, 236));
        oval.set(ix - .46f * r, iy - .41f * r, ix - .30f * r, iy - .31f * r);
        c.drawOval(oval, fill);

        // Shadow cast by the upper lid.
        c.drawRect(e.bounds, e.shadow);

        // Eyelid closing over the eye during a blink.
        float lidY = e.sUpy + (1f - lidOpen) * (e.sLoy - e.sUpy) * .92f;
        if (lidOpen < .999f) {
            e.skinPath.reset();
            e.skinPath.moveTo(e.sAx, e.sAy);
            e.skinPath.quadTo(e.sUpx, e.sUpy, e.sBx, e.sBy);
            e.skinPath.quadTo(e.sUpx, lidY, e.sAx, e.sAy);
            e.skinPath.close();
            fill.setColor(e.skin);
            c.drawPath(e.skinPath, fill);
        }

        // An inverse fill erases the whole layer outside the eyelids (DST_IN with the almond itself would only
        // touch the pixels the almond covers and leave the rest of the layer square).
        c.save();
        c.clipRect(e.bounds);
        c.drawPath(e.outside, mask);
        c.restore();

        e.lid.reset();
        e.lid.moveTo(e.sAx, e.sAy);
        e.lid.quadTo(e.sUpx, lidY, e.sBx, e.sBy);
        lash.setStrokeWidth(Math.max(1f, r * .075f));
        c.drawPath(e.lid, lash);

        if (e.cutPaint != null) c.drawRect(e.bounds.left, e.bounds.top, e.sCut + 3 * (r / e.r), e.bounds.bottom, e.cutPaint);
        c.restoreToCount(layer);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
