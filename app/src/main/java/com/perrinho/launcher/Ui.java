package com.perrinho.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Colores y ayudas de dibujo compartidas (todo se dibuja en código, sin imágenes). */
final class Ui {
    static float density = 1f;

    static final int TEXT = 0xFFE8ECF2;
    static final int MUTED = 0xFF9AA5B1;
    static final int CARD = 0xFF222934;
    static final int CARD_FOCUS = 0xFF34415A;
    static final int DARK = 0xFF10141A;
    static final int ACCENT = 0xFF2B3A55;

    private Ui() {
    }

    static int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Fondo redondeado que se resalta con borde blanco cuando está enfocado o seleccionado. */
    static Drawable focusCard(float radiusDp, int normal, int focused) {
        GradientDrawable f = round(focused, radiusDp);
        f.setStroke(Math.max(2, dp(2.5f)), Color.WHITE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_focused }, f);
        s.addState(new int[] { android.R.attr.state_selected }, f);
        s.addState(new int[] { android.R.attr.state_pressed }, f);
        s.addState(new int[] {}, round(normal, radiusDp));
        return s;
    }

    private static Drawable whiteWhenFocused(int normal, int activated, float radiusDp) {
        GradientDrawable on = round(Color.WHITE, radiusDp);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_focused }, on);
        s.addState(new int[] { android.R.attr.state_pressed }, on);
        s.addState(new int[] { android.R.attr.state_activated }, round(activated, radiusDp));
        s.addState(new int[] {}, round(normal, radiusDp));
        return s;
    }

    private static ColorStateList textColors(int focused, int activated, int normal) {
        return new ColorStateList(
                new int[][] {
                        { android.R.attr.state_focused },
                        { android.R.attr.state_pressed },
                        { android.R.attr.state_activated },
                        {}
                },
                new int[] { focused, focused, activated, normal });
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** Botón en forma de píldora (Wi-Fi, Ajustes, acciones). */
    static TextView pill(Context c, String text, View.OnClickListener l) {
        TextView t = text(c, text, 16, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(l);
        t.setBackgroundDrawable(whiteWhenFocused(CARD, CARD, 22));
        t.setTextColor(textColors(DARK, TEXT, TEXT));
        return t;
    }

    /** Pestaña de la barra superior. */
    static TextView tab(Context c, String text) {
        TextView t = text(c, text, 17, MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(22), dp(10), dp(22), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackgroundDrawable(whiteWhenFocused(Color.TRANSPARENT, 0x33FFFFFF, 22));
        t.setTextColor(textColors(DARK, Color.WHITE, MUTED));
        return t;
    }

    /** Elemento del menú lateral de Multimedia. */
    static TextView sideItem(Context c, String text, int glyph) {
        TextView t = text(c, text, 17, TEXT);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackgroundDrawable(whiteWhenFocused(Color.TRANSPARENT, ACCENT, 14));
        t.setTextColor(textColors(DARK, Color.WHITE, TEXT));
        GlyphDrawable g = new GlyphDrawable(glyph);
        g.setBounds(0, 0, dp(26), dp(26));
        t.setCompoundDrawables(g, null, null, null);
        t.setCompoundDrawablePadding(dp(14));
        return t;
    }
}
