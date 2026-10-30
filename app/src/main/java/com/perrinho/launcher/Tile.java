package com.perrinho.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tarjeta redondeada: icono sobre fondo y nombre debajo. Al enfocarla (o al seleccionarla
 * dentro de una cuadrícula) se agranda y muestra un borde blanco, como en Google TV.
 */
final class Tile extends LinearLayout {
    final ImageView icon;
    final TextView label;

    Tile(Context c, int cardHeightDp, int iconDp, boolean focusable) {
        super(c);
        setOrientation(VERTICAL);
        setClipChildren(false);
        if (focusable) {
            setFocusable(true);
            setClickable(true);
        }

        FrameLayout card = new FrameLayout(c);
        card.setBackgroundDrawable(Ui.focusCard(14, Ui.CARD, Ui.CARD_FOCUS));
        addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(cardHeightDp)));

        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(iconDp), Ui.dp(iconDp), Gravity.CENTER));

        label = new TextView(c);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(new ColorStateList(
                new int[][] { { android.R.attr.state_selected }, {} },
                new int[] { Color.WHITE, Ui.MUTED }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(8);
        addView(label, lp);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        // El foco se traduce en "seleccionado" para que los hijos (fondo y texto) cambien de estado
        setSelected(gainFocus);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        float scale = selected ? 1.08f : 1f;
        animate().cancel();
        animate().scaleX(scale).scaleY(scale).setDuration(110).start();
    }

    /** Al reciclar la vista en una cuadrícula: sin animación y sin selección. */
    void reset() {
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        super.setSelected(false);
    }
}
