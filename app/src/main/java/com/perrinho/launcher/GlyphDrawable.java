package com.perrinho.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Iconos sencillos dibujados con formas (carpeta, video, música, foto, USB, disco). */
final class GlyphDrawable extends Drawable {
    static final int FOLDER = 0;
    static final int VIDEO = 1;
    static final int MUSIC = 2;
    static final int IMAGE = 3;
    static final int USB = 4;
    static final int DISK = 5;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    GlyphDrawable(int type) {
        this.type = type;
    }

    private void rect(float l, float t, float rt, float b, float rad, Rect bd, int color) {
        float w = bd.width();
        float h = bd.height();
        r.set(bd.left + l * w, bd.top + t * h, bd.left + rt * w, bd.top + b * h);
        p.setColor(color);
        canvas.drawRoundRect(r, rad * w, rad * w, p);
    }

    private Canvas canvas;

    @Override
    public void draw(Canvas c) {
        canvas = c;
        Rect bd = getBounds();
        float w = bd.width();
        float h = bd.height();
        float x = bd.left;
        float y = bd.top;
        final int white = 0xFFFFFFFF;
        switch (type) {
            case FOLDER:
                rect(0.08f, 0.18f, 0.48f, 0.42f, 0.06f, bd, 0xFFE0A030);
                rect(0.05f, 0.30f, 0.95f, 0.82f, 0.08f, bd, 0xFFF2B33D);
                break;
            case VIDEO:
                rect(0.08f, 0.2f, 0.92f, 0.8f, 0.10f, bd, 0xFFEF5B5B);
                path.reset();
                path.moveTo(x + 0.42f * w, y + 0.34f * h);
                path.lineTo(x + 0.42f * w, y + 0.66f * h);
                path.lineTo(x + 0.68f * w, y + 0.50f * h);
                path.close();
                p.setColor(white);
                c.drawPath(path, p);
                break;
            case MUSIC:
                rect(0.08f, 0.08f, 0.92f, 0.92f, 0.14f, bd, 0xFF3FA9E0);
                p.setColor(white);
                c.drawCircle(x + 0.40f * w, y + 0.70f * h, 0.11f * w, p);
                c.drawRect(x + 0.47f * w, y + 0.24f * h, x + 0.54f * w, y + 0.70f * h, p);
                c.drawRect(x + 0.54f * w, y + 0.24f * h, x + 0.72f * w, y + 0.36f * h, p);
                break;
            case IMAGE:
                rect(0.08f, 0.16f, 0.92f, 0.84f, 0.10f, bd, 0xFF4CAF6A);
                p.setColor(white);
                c.drawCircle(x + 0.32f * w, y + 0.36f * h, 0.08f * w, p);
                path.reset();
                path.moveTo(x + 0.14f * w, y + 0.76f * h);
                path.lineTo(x + 0.42f * w, y + 0.46f * h);
                path.lineTo(x + 0.60f * w, y + 0.66f * h);
                path.lineTo(x + 0.72f * w, y + 0.54f * h);
                path.lineTo(x + 0.86f * w, y + 0.76f * h);
                path.close();
                c.drawPath(path, p);
                break;
            case USB:
                rect(0.30f, 0.38f, 0.70f, 0.94f, 0.08f, bd, 0xFFB39DDB);
                rect(0.38f, 0.10f, 0.62f, 0.40f, 0.03f, bd, 0xFF9575CD);
                rect(0.43f, 0.16f, 0.48f, 0.26f, 0.01f, bd, white);
                rect(0.52f, 0.16f, 0.57f, 0.26f, 0.01f, bd, white);
                break;
            default:
                rect(0.06f, 0.30f, 0.94f, 0.72f, 0.08f, bd, 0xFF90A4AE);
                p.setColor(white);
                c.drawCircle(x + 0.80f * w, y + 0.62f * h, 0.035f * w, p);
                break;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        p.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        p.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
