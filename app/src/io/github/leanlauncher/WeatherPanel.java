package io.github.leanlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Wetter-Kachel im Stil gaengiger Wetter-Apps (Apple Weather / Google Wetter):
 * links große aktuelle Temperatur, rechts 7 Tage als Spalten mit
 * gezeichnetem Symbol, Temperaturspanne und Regenwahrscheinlichkeit.
 *
 * Warum gezeichnet und nicht Emoji: Emoji skalieren auf dem Fire TV
 * unkontrolliert, haben Farbränder und sind bei 1080p auf 3 m Entfernung
 * nicht lesbar. Alle Symbole hier sind Vektorformen auf Canvas und
 * damit scharf und in der Groesse steuerbar.
 */
final class WeatherPanel extends FrameLayout {

    private static final int SUN = Color.rgb(255, 199, 74);
    private static final int MOON = Color.rgb(214, 222, 245);
    private static final int CLOUD = Color.rgb(178, 188, 202);
    private static final int CLOUD_DARK = Color.rgb(142, 153, 170);
    private static final int RAIN = Color.rgb(120, 178, 255);
    private static final int SNOW = Color.rgb(232, 240, 255);
    private static final int BOLT = Color.rgb(255, 214, 92);
    private static final int HOT = Color.rgb(255, 142, 92);
    private static final int COLD = Color.rgb(132, 186, 255);
    private static final int MILD = Color.rgb(240, 240, 244);

    private final Context ctx;
    private final float dp;

    private TextView cityLine, tempBig, condLine, metaLine;
    private LinearLayout strip;

    WeatherPanel(Context c) {
        super(c);
        this.ctx = c;
        this.dp = c.getResources().getDisplayMetrics().density;
        build();
    }

    private int d(int v) { return (int) (v * dp + 0.5f); }

    private void build() {
        setBackground(Ui.weatherCard(dp));
        // Klickbar: oeffnet die Tages-Detailansicht (Stundenverlauf, Messwerte,
        // Sonnenzeiten). Auf der flachen Kachel ist dafuer kein Platz.
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    HomeService.pauseFor(10 * 60 * 1000);
                    ctx.startActivity(new android.content.Intent(ctx,
                            WeatherDetailActivity.class));
                } catch (RuntimeException e) {
                    android.util.Log.w("LEANW", "Wetter-Detail fehlgeschlagen: " + e);
                }
            }
        });
        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(has ? Ui.round(Color.rgb(38, 40, 48), dp, 16)
                                    : Ui.weatherCard(dp));
                v.animate().scaleX(has ? 1.01f : 1f).scaleY(has ? 1.01f : 1f)
                        .setDuration(100).start();
            }
        });

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Innenabstand: bei 440 dp Kachelbreite bleiben 440-16-14 = 410 dp
        // fuer Ist-Wert (190) + 8 Abstand + 7 Tage (212, je 30 dp).
        row.setPadding(d(16), d(8), d(14), d(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        // ---- links: Ist-Wert, kompakt ----
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);

        IconView bigIcon = new IconView(ctx);
        bigIcon.setCode(-1, true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(d(46), d(46));
        ip.rightMargin = d(8);
        left.addView(bigIcon, ip);
        this.bigIcon = bigIcon;

        LinearLayout leftText = new LinearLayout(ctx);
        leftText.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tempRow = new LinearLayout(ctx);
        tempRow.setOrientation(LinearLayout.HORIZONTAL);
        tempRow.setGravity(Gravity.BOTTOM);

        tempBig = new TextView(ctx);
        tempBig.setTextColor(Ui.TEXT);
        tempBig.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tempBig.setTypeface(android.graphics.Typeface.create("sans-serif-thin",
                android.graphics.Typeface.NORMAL));
        tempBig.setIncludeFontPadding(false);
        tempRow.addView(tempBig);

        cityLine = new TextView(ctx);
        cityLine.setTextColor(Ui.TEXT_DIM);
        cityLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cityLine.setLetterSpacing(0.05f);
        cityLine.setIncludeFontPadding(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = d(7);
        clp.bottomMargin = d(4);
        tempRow.addView(cityLine, clp);
        leftText.addView(tempRow);

        condLine = new TextView(ctx);
        condLine.setTextColor(Ui.TEXT);
        condLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        condLine.setIncludeFontPadding(false);
        condLine.setSingleLine(true);
        leftText.addView(condLine);

        metaLine = new TextView(ctx);
        metaLine.setTextColor(Ui.TEXT_DIM);
        metaLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        metaLine.setSingleLine(true);
        leftText.addView(metaLine);

        left.addView(leftText);

        row.addView(left, new LinearLayout.LayoutParams(
                d(196), ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- rechts: 8 Tage ----
        strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(strip, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private IconView bigIcon;

    /**
     * Handlungszeile: die Antwort, nicht die Messwerte.
     *
     * "2°" allein sagt niemandem etwas. "Jacke reicht, kein Schirm" schon.
     * Die Regeln sind bewusst einfach und nachvollziehbar - kein ML, nur
     * Temperatur, Regenrisiko und Wind kombiniert.
     */
    static String actionLine(Weather.Info wi) {
        StringBuilder sb = new StringBuilder();

        // 1. Kleidung nach gefuehlter Temperatur
        int f = wi.feels;
        if (f <= -5) sb.append("Dicke Jacke");
        else if (f <= 3) sb.append("Jacke");
        else if (f <= 9) sb.append("Jacke reicht");
        else if (f <= 15) sb.append("Leichte Jacke");
        else if (f <= 22) sb.append("Pullover reicht");
        else if (f <= 28) sb.append("Kurzarm");
        else sb.append("Hitze, Schatten suchen");

        // 2. Schirm - nur wenn Regen realistisch ist
        int rain = wi.rain;
        boolean wetNow = wi.code >= 51 && wi.code <= 67 || wi.code >= 80 && wi.code <= 82
                || wi.code >= 95;
        if (wetNow) sb.append(", Schirm mit");
        else if (rain >= 60) sb.append(", Schirm einpacken");
        else if (rain >= 30) sb.append(", Schirm schadet nicht");
        else sb.append(", kein Schirm");

        // 3. Wind nur bei relevanter Staerke
        if (wi.wind >= 50) sb.append(" · stürmisch");
        else if (wi.wind >= 35) sb.append(" · windig");

        return sb.toString();
    }

    /** Daten einsetzen. Wird aus dem UI-Thread aufgerufen. */
    void show(Weather.Info wi) {
        if (wi == null || !wi.valid()) {
            cityLine.setText(Weather.CITY.toUpperCase(Locale.GERMANY));
            tempBig.setText("--°");
            condLine.setText("Wetter wird geladen");
            metaLine.setText("");
            bigIcon.setCode(-1, true);
            strip.removeAllViews();
            return;
        }

        cityLine.setText(Weather.CITY.toUpperCase(Locale.GERMANY));
        tempBig.setText(wi.temp + "°");
        bigIcon.setCode(wi.code, wi.day);
        condLine.setText(actionLine(wi));

        // Messwerte klein darunter - lesbar ab 12 sp, nicht mehr 9.
        StringBuilder m = new StringBuilder();
        m.append(wi.max).append("° / ").append(wi.min).append("°");
        m.append("  ·  gefühlt ").append(wi.feels).append("°");
        if (wi.rain > 0) m.append("  ·  ").append(wi.rain).append(" % Regen");
        metaLine.setText(m.toString());

        strip.removeAllViews();
        Weather.Day[] days = wi.days;
        android.util.Log.i("LEANW", "Panel zeigt " + (days == null ? -1 : days.length) + " Tage");
        if (days == null || days.length == 0) return;

        // Heute + 6 Folgetage: 7 Spalten * 30 dp = 210 dp, das passt
        // neben den Ist-Wert in eine 440-dp-Kachel. Acht Spalten ergaben
        // je 26 dp - dann brachen "Jetzt"/"11%" um und der Streifen lief
        // rechts aus der Kachel heraus.
        int n = Math.min(days.length, 7);
        for (int i = 0; i < n; i++) {
            strip.addView(buildColumn(days[i], i == 0),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    /** Eine Spalte: "Mo", Symbol, Max/Min, Regen. Kompakt fuer die flache Kachel. */
    private View buildColumn(Weather.Day day, boolean first) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(d(1), d(3), d(1), d(3));

        if (first) {
            // Heute-Spalte leicht hervorheben
            col.setBackground(Ui.round(Color.argb(26, 247, 148, 30), dp, 8));
        }

        TextView wd = new TextView(ctx);
        wd.setText(dayName(day.date, first));
        wd.setTextColor(first ? Ui.ACCENT : Ui.TEXT_DIM);
        wd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        wd.setGravity(Gravity.CENTER);
        wd.setMaxLines(1);
        wd.setSingleLine(true);
        wd.setLetterSpacing(0.04f);
        col.addView(wd);

        IconView ic = new IconView(ctx);
        ic.setCode(day.code, true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(d(26), d(26));
        ilp.topMargin = d(3);
        ilp.bottomMargin = d(2);
        col.addView(ic, ilp);

        TextView hi = new TextView(ctx);
        hi.setText(String.valueOf(day.max) + "°");
        hi.setTextColor(tempColor(day.max));
        hi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hi.setGravity(Gravity.CENTER);
        hi.setIncludeFontPadding(false);
        hi.setSingleLine(true);
        col.addView(hi);

        TextView lo = new TextView(ctx);
        lo.setText(String.valueOf(day.min) + "°");
        lo.setTextColor(Ui.TEXT_DIM);
        lo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lo.setGravity(Gravity.CENTER);
        lo.setIncludeFontPadding(false);
        lo.setSingleLine(true);
        col.addView(lo);

        TextView rr = new TextView(ctx);
        rr.setText(day.rain > 0 ? day.rain + "%" : "");
        rr.setTextColor(RAIN);
        rr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        rr.setGravity(Gravity.CENTER);
        rr.setSingleLine(true);
        rr.setPadding(0, d(2), 0, 0);
        col.addView(rr);

        return col;
    }

    /** Temperaturfarbe wie in Wetter-Apps: kalt blau, warm orange. */
    static int tempColor(int t) {
        if (t <= -5) return COLD;
        if (t <= 5) return Color.rgb(160, 200, 255);
        if (t <= 14) return MILD;
        if (t <= 24) return Color.rgb(255, 226, 168);
        return HOT;
    }

    /** "2026-11-23" -> "Mo", heute -> "Heute". */
    static String dayName(String iso, boolean today) {
        if (today) return "Jetzt";
        int y = 0, mo = 0, da = 0;
        try {
            String[] p = iso.split("-");
            y = Integer.parseInt(p[0]);
            mo = Integer.parseInt(p[1]) - 1;
            da = Integer.parseInt(p[2]);
        } catch (RuntimeException e) {
            return "";
        }
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.set(y, mo, da);
        String[] names = {"So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"};
        return names[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    // ================= gezeichnetes Wetter-Symbol =================

    /** Zeichnet Sonne, Mond, Wolken, Regen, Schnee, Nebel oder Gewitter. */
    static final class IconView extends View {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Path path = new Path();
        private int code = -1;
        private boolean day = true;

        IconView(Context c) {
            super(c);
            p.setStyle(Paint.Style.FILL);
        }

        void setCode(int c, boolean isDay) {
            if (this.code == c && this.day == isDay) return;
            this.code = c;
            this.day = isDay;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (code < 0) {
                p.setColor(Color.argb(60, 255, 255, 255));
                cv.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.30f, p);
                return;
            }
            drawCode(cv, (int) w, (int) h, code, day);
        }

        /** Wird auch fuer die grosse Ist-Kachel mit groesserem Platz genutzt. */
        void drawCode(Canvas cv, int w, int h, int code, boolean day) {
            float s = Math.min(w, h);
            float cx = w / 2f, cy = h / 2f;

            boolean hasSun = (code == 0 || code == 1);
            boolean partCloud = (code == 1 || code == 2);
            boolean cloudOnly = (code == 3 || (code >= 45 && code <= 48));
            boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
            boolean snow = (code >= 71 && code <= 77) || (code >= 85 && code <= 86);
            boolean fog = (code == 45 || code == 48);
            boolean storm = (code >= 95);

            if (hasSun) {
                if (day) {
                    float r = s * (partCloud ? 0.20f : 0.30f);
                    float ox = partCloud ? -s * 0.14f : 0f;
                    float oy = partCloud ? -s * 0.15f : 0f;
                    drawSun(cv, cx + ox, cy + oy, r);
                } else {
                    drawMoon(cv, cx + (partCloud ? -s * 0.13f : 0f),
                            cy + (partCloud ? -s * 0.12f : 0f), s * 0.24f);
                }
            }

            if (partCloud || cloudOnly || rain || snow || storm) {
                float sc = cloudOnly || rain || snow || storm ? 1f : 0.78f;
                float ox = cloudOnly || rain || snow || storm ? 0f : s * 0.12f;
                float oy = cloudOnly || rain || snow || storm ? -s * 0.05f : s * 0.10f;
                drawCloud(cv, cx + ox, cy + oy, s * 0.62f * sc,
                        cloudOnly || rain || snow || storm ? CLOUD : CLOUD);
            }

            if (rain) {
                p.setColor(RAIN);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, s * 0.055f));
                float y0 = cy + s * 0.24f;
                float len = s * 0.16f;
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * s * 0.17f;
                    cv.drawLine(x, y0, x - s * 0.045f, y0 + len, p);
                }
                p.setStyle(Paint.Style.FILL);
            } else if (snow) {
                p.setColor(SNOW);
                for (int i = -1; i <= 1; i++) {
                    cv.drawCircle(cx + i * s * 0.17f, cy + s * 0.30f + (i == 0 ? s * 0.03f : 0f),
                            s * 0.045f, p);
                }
            } else if (storm) {
                p.setColor(BOLT);
                float x = cx, y = cy + s * 0.16f;
                path.reset();
                path.moveTo(x + s * 0.05f, y);
                path.lineTo(x - s * 0.07f, y + s * 0.15f);
                path.lineTo(x + s * 0.01f, y + s * 0.15f);
                path.lineTo(x - s * 0.05f, y + s * 0.30f);
                path.lineTo(x + s * 0.10f, y + s * 0.11f);
                path.lineTo(x + s * 0.02f, y + s * 0.11f);
                path.close();
                cv.drawPath(path, p);
            } else if (fog) {
                p.setColor(CLOUD_DARK);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, s * 0.05f));
                for (int i = 0; i < 3; i++) {
                    float y = cy + s * 0.14f + i * s * 0.10f;
                    float inset = (i % 2 == 0) ? s * 0.10f : s * 0.04f;
                    cv.drawLine(cx - s * 0.26f + inset, y, cx + s * 0.26f - inset, y, p);
                }
                p.setStyle(Paint.Style.FILL);
            }
        }

        private void drawSun(Canvas cv, float x, float y, float r) {
            p.setColor(SUN);
            // Strahlen
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(2f, r * 0.22f));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                float x1 = (float) (x + Math.cos(a) * r * 1.42f);
                float y1 = (float) (y + Math.sin(a) * r * 1.42f);
                float x2 = (float) (x + Math.cos(a) * r * 1.82f);
                float y2 = (float) (y + Math.sin(a) * r * 1.82f);
                cv.drawLine(x1, y1, x2, y2, p);
            }
            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(x, y, r, p);
        }

        private void drawMoon(Canvas cv, float x, float y, float r) {
            // Ohne Layer/CLEAR malen: Sichel als zwei Kreise - echter Kreis plus
            // ein Kreis in Hintergrundfarbe, der die Kerbe ausstanzt.
            p.setColor(MOON);
            cv.drawCircle(x, y, r, p);
            p.setColor(Ui.CARD);
            cv.drawCircle(x + r * 0.58f, y - r * 0.34f, r * 0.88f, p);
            p.setColor(MOON);
        }

        /** Wolke aus drei Kreisen plus Balken. w = Breite. */
        private void drawCloud(Canvas cv, float cx, float cy, float w, int color) {
            p.setColor(color);
            float r1 = w * 0.22f;
            float r2 = w * 0.28f;
            float r3 = w * 0.19f;
            cv.drawCircle(cx - w * 0.20f, cy + w * 0.02f, r1, p);
            cv.drawCircle(cx + w * 0.02f, cy - w * 0.06f, r2, p);
            cv.drawCircle(cx + w * 0.22f, cy + w * 0.03f, r3, p);
            rf.set(cx - w * 0.22f, cy + w * 0.00f, cx + w * 0.24f, cy + w * 0.20f);
            cv.drawRoundRect(rf, w * 0.10f, w * 0.10f, p);
        }
    }
}
