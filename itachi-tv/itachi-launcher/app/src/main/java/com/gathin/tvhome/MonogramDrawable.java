package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The Itachi mark for an app with no usable logo: its initial, in a clean bold face, with a red notch. */
final class MonogramDrawable extends Drawable {
    private final String letter;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;

    MonogramDrawable(String label, float density) {
        String l = label == null ? "" : label.trim();
        letter = l.isEmpty() ? "?" : l.substring(0, l.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        size = Math.round(48 * density);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        accent.setColor(Color.rgb(226, 18, 30));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float h = b.height();
        text.setTextSize(h * .86f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float y = b.exactCenterY() - (fm.ascent + fm.descent) / 2f - h * .04f;
        c.drawText(letter, b.exactCenterX(), y, text);
        float w = h * .34f;
        c.drawRoundRect(b.exactCenterX() - w / 2f, b.bottom - h * .07f, b.exactCenterX() + w / 2f, b.bottom,
                h * .035f, h * .035f, accent);
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { text.setAlpha(alpha); accent.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { text.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
