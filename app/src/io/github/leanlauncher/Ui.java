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

    /** Karte fuer die Wetter-Kachel: leichter Verlauf, damit sie sich abhebt. */
    static GradientDrawable weatherCard(float dp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(30, 32, 38), Color.rgb(19, 20, 24)});
        d.setCornerRadius(16 * dp);
        return d;
    }

    /** ID der ersten grossen Kachel - Ziel fuer D-Pad "runter". */
    static final int KACHEL_ID = 0x7f0f0002;

    /**
     * Fokus-Rahmen nach Android-TV-Guideline: 3 dp Border, Radius 8 dp.
     * Farbe allein reicht auf 3 m Entfernung nicht - der Rahmen macht den
     * Fokus auch bei hellem Bildinhalt eindeutig.
     */
    static GradientDrawable focusRing(float dp, int fill, int ring, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius * dp);
        d.setStroke((int) Math.max(2f, 3 * dp), ring);
        return d;
    }
}
