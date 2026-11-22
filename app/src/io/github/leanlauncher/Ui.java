package io.github.leanlauncher;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * Zentrale Farben und Masse.
 *
 * Hintergrund bewusst fast schwarz mit minimalem Verlauf (HDR-freundlich) -
 * kein Blaugrau, das auf Fernsehern ausgewaschen wirkt.
 */
final class Ui {

    private Ui() { }

    // Hintergrund: fast schwarz, Verlauf von #0A0A0C nach #000000
    static final int BG_TOP = Color.rgb(10, 10, 12);
    static final int BG_BOTTOM = Color.rgb(0, 0, 0);

    // Karten
    static final int CARD = Color.rgb(22, 22, 26);
    static final int CARD_FOCUS = Color.rgb(247, 148, 30);

    // Text
    static final int TEXT = Color.rgb(240, 240, 244);
    static final int TEXT_DIM = Color.rgb(140, 140, 152);
    static final int TEXT_ON_FOCUS = Color.rgb(24, 18, 10);

    // Akzent
    static final int ACCENT = Color.rgb(247, 148, 30);
    static final int LIVE = Color.rgb(226, 32, 42);

    static GradientDrawable bg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM});
    }

    static GradientDrawable card(float dp, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(radius * dp);
        return d;
    }

    static GradientDrawable round(int color, float dp, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius * dp);
        return d;
    }
}
