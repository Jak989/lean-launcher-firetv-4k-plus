package io.github.leanlauncher;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Wetter-Detailansicht fuer heute.
 *
 * Zeigt, was auf dem Startbildschirm keinen Platz hat:
 *   - grosser Ist-Wert mit Symbol und Beschreibung
 *   - gefuehlte Temperatur, Luftfeuchte, Wind, Niederschlagsrisiko
 *   - Sonnenauf- und -untergang
 *   - Temperaturverlauf heute (gezeichnete Kurve)
 *   - die naechsten Tage als Zeilen mit Balken fuer Min/Max
 *
 * Bedienung: DPAD hoch/runter scrollt, OK blaettert, BACK schliesst.
 */
public class WeatherDetailActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private float dp;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        buildUi();
    }

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        HomeService.pauseFor(10 * 60 * 1000);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) hideSystemUi();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(24), dp(56), dp(28));

        Weather.Info wi = Weather.cached(this);
        Weather.refresh(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        // Daten sind da -> Ansicht neu aufbauen
                        buildUi();
                    }
                });
            }
        });

        // ---- Kopf ----
        TextView head = new TextView(this);
        head.setText("WETTER  ·  " + Weather.CITY.toUpperCase(Locale.GERMANY));
        head.setTextColor(Ui.ACCENT);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.18f);
        page.addView(head);

        if (!wi.valid()) {
            TextView t = new TextView(this);
            t.setText("Wetterdaten werden geladen …");
            t.setTextColor(Ui.TEXT_DIM);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            t.setPadding(0, dp(20), 0, 0);
            page.addView(t);
            addScroll(root, page);
            return;
        }

        // ---- Ist-Wert gross ----
        LinearLayout nowRow = new LinearLayout(this);
        nowRow.setOrientation(LinearLayout.HORIZONTAL);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        nowRow.setPadding(0, dp(10), 0, dp(16));

        WeatherPanel.IconView big = new WeatherPanel.IconView(this);
        big.setCode(wi.code, wi.day);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(84), dp(84));
        blp.rightMargin = dp(22);
        nowRow.addView(big, blp);

        LinearLayout nowText = new LinearLayout(this);
        nowText.setOrientation(LinearLayout.VERTICAL);

        TextView temp = new TextView(this);
        temp.setText(wi.temp + "°");
        temp.setTextColor(Ui.TEXT);
        temp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        temp.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        temp.setIncludeFontPadding(false);
        nowText.addView(temp);

        TextView cond = new TextView(this);
        cond.setText(wi.label + "   ·   Höchst " + wi.max + "°   ·   Tief " + wi.min + "°");
        cond.setTextColor(Ui.TEXT);
        cond.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        nowText.addView(cond);

        // Handlungszeile: die eigentliche Antwort auf "was zieh ich an"
        TextView action = new TextView(this);
        action.setText(WeatherPanel.actionLine(wi));
        action.setTextColor(Ui.ACCENT);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        action.setPadding(0, dp(4), 0, 0);
        nowText.addView(action);

        TextView feel = new TextView(this);
        feel.setText("Gefühlt wie " + wi.feels + "°");
        feel.setTextColor(Ui.TEXT_DIM);
        feel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        feel.setPadding(0, dp(4), 0, 0);
        nowText.addView(feel);

        nowRow.addView(nowText);
        page.addView(nowRow);

        // ---- Messwerte als Kachelreihe ----
        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.HORIZONTAL);

        facts.addView(factCard("NIEDERSCHLAG", wi.rain >= 0 ? wi.rain + " %" : "–",
                wi.rain >= 60 ? "wahrscheinlich" : (wi.rain >= 25 ? "möglich" : "unwahrscheinlich")),
                weight(1f));
        facts.addView(factCard("LUFTFEUCHTE", wi.humidity + " %",
                wi.humidity >= 80 ? "schwül" : (wi.humidity >= 50 ? "angenehm" : "trocken")),
                weight(1f));
        facts.addView(factCard("WIND", wi.wind + " km/h",
                wi.wind >= 40 ? "kräftig" : (wi.wind >= 15 ? "spürbar" : "kaum")),
                weight(1f));
        facts.addView(factCard("SONNE",
                wi.sunrise.length() > 0 ? wi.sunrise : "–",
                wi.sunset.length() > 0 ? "Untergang " + wi.sunset : ""),
                weight(1f));

        page.addView(facts, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Verlauf heute (gezeichnet) ----
        TextView chartHead = new TextView(this);
        chartHead.setText("VERLAUF HEUTE");
        chartHead.setTextColor(Ui.TEXT_DIM);
        chartHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chartHead.setTypeface(Typeface.DEFAULT_BOLD);
        chartHead.setLetterSpacing(0.16f);
        chartHead.setPadding(0, dp(20), 0, dp(8));
        page.addView(chartHead);

        HourChart chart = new HourChart(this);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        page.addView(chart, chlp);
        chart.setData(wi);

        // ---- naechste Tage ----
        TextView dayHead = new TextView(this);
        dayHead.setText("DIE NÄCHSTEN TAGE");
        dayHead.setTextColor(Ui.TEXT_DIM);
        dayHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dayHead.setTypeface(Typeface.DEFAULT_BOLD);
        dayHead.setLetterSpacing(0.16f);
        dayHead.setPadding(0, dp(20), 0, dp(8));
        page.addView(dayHead);

        Weather.Day[] days = wi.days;
        if (days != null && days.length > 1) {
            for (int i = 0; i < days.length && i < 8; i++) {
                page.addView(dayRow(days[i], i == 0, wi));
            }
        }

        // ---- Stundenverlauf: die naechsten 24 Stunden im Detail ----
        TextView hourHead = new TextView(this);
        hourHead.setText("STUNDE FÜR STUNDE");
        hourHead.setTextColor(Ui.TEXT_DIM);
        hourHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hourHead.setTypeface(Typeface.DEFAULT_BOLD);
        hourHead.setLetterSpacing(0.16f);
        hourHead.setPadding(0, dp(22), 0, dp(3));
        page.addView(hourHead);

        addHourTable(page, wi);

        TextView hint = new TextView(this);
        hint.setText("▲▼  =  scrollen          BACK  =  zurück zur Startseite");
        hint.setTextColor(Color.rgb(100, 100, 112));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(0, dp(20), 0, 0);
        page.addView(hint);

        addScroll(root, page);
    }

    /**
     * Stundentabelle: Zeit, Wettersymbol, Temperatur, Regenmenge in mm,
     * Regenwahrscheinlichkeit, Wind und Luftfeuchte.
     *
     * Zeigt 24 Stunden ab der aktuellen Stunde. Die erste Zeile ist
     * hervorgehoben - das ist "jetzt".
     */
    private void addHourTable(LinearLayout page, Weather.Info wi) {
        if (wi.hourTemp == null || wi.hourTemp.length < 6) {
            TextView t = new TextView(this);
            t.setText("Stundenwerte noch nicht geladen.");
            t.setTextColor(Ui.TEXT_DIM);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setPadding(0, dp(6), 0, 0);
            page.addView(t);
            return;
        }

        int nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int start = Math.min(nowHour, Math.max(0, wi.hourTemp.length - 24));

        // Spaltenkoepfe
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(dp(4), dp(2), dp(4), dp(5));
        addCell(head, "ZEIT", dp(70), 0f, 10, true, Color.rgb(104, 104, 118));
        addCell(head, "", dp(34), 0f, 10, true, Color.rgb(104, 104, 118));
        addCell(head, "TEMP", dp(64), 0f, 10, true, Color.rgb(104, 104, 118));
        addCell(head, "REGEN", dp(84), 0f, 10, true, Color.rgb(104, 104, 118));
        addCell(head, "RISIKO", dp(84), 0f, 10, true, Color.rgb(104, 104, 118));
        addCell(head, "WIND", dp(84), 0f, 10, true, Color.rgb(104, 104, 118));
        addCell(head, "FEUCHTE", dp(0), 1f, 10, true, Color.rgb(104, 104, 118));
        page.addView(head);

        int count = 0;
        for (int i = start; i < wi.hourTemp.length && count < 24; i++, count++) {
            boolean isNow = (i == nowHour);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(6), dp(4), dp(6));
            if (isNow) {
                row.setBackground(Ui.round(Color.argb(30, 247, 148, 30), dp, 7));
            } else if (count % 2 == 1) {
                row.setBackgroundColor(Color.argb(12, 255, 255, 255));
            }

            // Zeit
            addCell(row, isNow ? "Jetzt" : String.format("%02d:00", i % 24),
                    dp(70), 0f, 13, false, isNow ? Ui.ACCENT : Ui.TEXT);

            // Symbol
            WeatherPanel.IconView ic = new WeatherPanel.IconView(this);
            ic.setCode(i < wi.hourCode.length ? wi.hourCode[i] : -1,
                    (i >= 7 && i < 19));
            row.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));

            // Temperatur
            addCell(row, wi.hourTemp[i] + "°", dp(64), 0f, 14, false,
                    Ui.TEXT);

            // Regenmenge: nur zeigen wenn > 0, sonst Strich
            int mm10 = i < wi.hourMm.length ? wi.hourMm[i] : 0;
            String mmTxt = mm10 > 0
                    ? String.format(java.util.Locale.GERMANY, "%.1f mm", mm10 / 10.0)
                    : "–";
            addCell(row, mmTxt, dp(84), 0f, 13, false,
                    mm10 > 0 ? Color.rgb(120, 178, 255) : Ui.TEXT_DIM);

            // Regenwahrscheinlichkeit
            int risk = i < wi.hourRain.length ? wi.hourRain[i] : -1;
            addCell(row, risk >= 0 ? risk + " %" : "–", dp(84), 0f, 13, false,
                    risk >= 50 ? Color.rgb(120, 178, 255) : Ui.TEXT_DIM);

            // Wind
            int wnd = i < wi.hourWind.length ? wi.hourWind[i] : 0;
            addCell(row, wnd + " km/h", dp(84), 0f, 13, false, Ui.TEXT_DIM);

            // Luftfeuchte
            int hum = i < wi.hourHum.length ? wi.hourHum[i] : 0;
            addCell(row, hum + " %", 0, 1f, 13, false, Ui.TEXT_DIM);

            page.addView(row);
        }
    }

    /** Eine Tabellenzelle. */
    private void addCell(LinearLayout row, String text, int width, float weight,
                         int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setSingleLine(true);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        if (width > 0) {
            row.addView(t, new LinearLayout.LayoutParams(width,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            row.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        }
    }

    private LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.rightMargin = dp(8);
        return lp;
    }

    private void addScroll(FrameLayout root, LinearLayout page) {
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        root.removeAllViews();
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    /** Kleine Karte: Label oben, grosser Wert, kleine Einordnung. */
    private View factCard(String label, String value, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.card(dp, 12));
        c.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.TEXT_DIM);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setLetterSpacing(0.12f);
        c.addView(l);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Ui.TEXT);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setPadding(0, dp(3), 0, 0);
        c.addView(v);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Color.rgb(120, 120, 134));
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        c.addView(s);

        return c;
    }

    /** Eine Tageszeile: Wochentag, Symbol, Balken, Min/Max, Regen. */
    private View dayRow(Weather.Day d, boolean today, Weather.Info wi) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));

        TextView wd = new TextView(this);
        wd.setText(WeatherPanel.dayName(d.date, today));
        wd.setTextColor(today ? Ui.ACCENT : Ui.TEXT);
        wd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(wd, new LinearLayout.LayoutParams(dp(80),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        WeatherPanel.IconView ic = new WeatherPanel.IconView(this);
        ic.setCode(d.code, true);
        row.addView(ic, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView cond = new TextView(this);
        cond.setText(Weather.shortLabel(d.code));
        cond.setTextColor(Ui.TEXT_DIM);
        cond.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(110),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(10);
        row.addView(cond, clp);

        // Balken: Min/Max im Verhaeltnis zur Woche
        RangeBar bar = new RangeBar(this);
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(8), 1f));
        bar.setRange(d.min, d.max, wi);

        TextView vals = new TextView(this);
        vals.setText(d.min + "°  /  " + d.max + "°");
        vals.setTextColor(Ui.TEXT);
        vals.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        vals.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(96),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(12);
        row.addView(vals, vlp);

        TextView rain = new TextView(this);
        rain.setText(d.rain > 0 ? d.rain + " %" : "");
        rain.setTextColor(Color.rgb(120, 178, 255));
        rain.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        rain.setGravity(Gravity.RIGHT);
        row.addView(rain, new LinearLayout.LayoutParams(dp(48),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View sep = new View(this);
        return row;
    }

    // ================= gezeichnete Elemente =================

    /** Temperaturkurve fuer heute. Nutzt die Tageswerte als Stuetzpunkte. */
    static final class HourChart extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path line = new Path();
        private final Path area = new Path();
        private int[] temps = new int[0];
        private int[] rain;
        private boolean real = false;
        private int min, max;

        HourChart(android.content.Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
        }

        void setData(Weather.Info wi) {
            // Echte Stundenwerte, wenn vorhanden. Frueher wurde die Kurve aus
            // Min/Max geformt - die sah immer gleich aus und war damit falsch.
            if (wi.hourTemp != null && wi.hourTemp.length >= 12) {
                // Ab der aktuellen Stunde zeigen - sonst steht links die Nacht,
                // die schon vorbei ist.
                int nowH = java.util.Calendar.getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY);
                int from = Math.min(nowH, Math.max(0, wi.hourTemp.length - 12));
                int n = Math.min(24, wi.hourTemp.length - from);
                temps = new int[n];
                rain = new int[n];
                for (int k = 0; k < n; k++) {
                    temps[k] = wi.hourTemp[from + k];
                    rain[k] = wi.hourRain.length > from + k ? wi.hourRain[from + k] : -1;
                }
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int t : temps) {
                    if (t <= -900) continue;
                    if (t < lo) lo = t;
                    if (t > hi) hi = t;
                }
                if (lo == Integer.MAX_VALUE) { temps = new int[0]; return; }
                min = lo;
                max = hi;
                real = true;
                invalidate();
                return;
            }

            // Notfall ohne Netzdaten: grober Tagesbogen aus Min/Max.
            int lo = wi.min, hi = wi.max;
            double[] day = {0.10, 0.05, 0.02, 0.00, 0.08, 0.35, 0.62, 0.85,
                            1.00, 0.94, 0.72, 0.45};
            int[] shape = new int[12];
            for (int i = 0; i < 12; i++) {
                shape[i] = (int) Math.round(lo + (hi - lo) * day[i]);
            }
            temps = shape;
            rain = null;
            min = lo;
            max = hi;
            real = false;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (temps.length < 2) return;
            float w = getWidth(), h = getHeight();
            float padL = 34f, padR = 8f, padT = 14f, padB = 30f;
            float plotW = w - padL - padR;
            float plotH = h - padT - padB;

            p.setStrokeWidth(Math.max(2f, h * 0.018f));
            p.setColor(Ui.ACCENT);
            fill.setColor(Color.argb(40, 247, 148, 30));

            float span = Math.max(1, max - min);
            line.reset();
            area.reset();

            for (int i = 0; i < temps.length; i++) {
                float x = padL + plotW * i / (temps.length - 1);
                float y = padT + plotH * (1f - (temps[i] - min) / span);
                if (i == 0) {
                    line.moveTo(x, y);
                    area.moveTo(x, padT + plotH);
                    area.lineTo(x, y);
                } else {
                    line.lineTo(x, y);
                    area.lineTo(x, y);
                }
            }
            area.lineTo(padL + plotW, padT + plotH);
            area.close();

            cv.drawPath(area, fill);
            cv.drawPath(line, p);

            // Stundenbeschriftung + Werte
            Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
            txt.setColor(Color.rgb(130, 130, 144));
            txt.setTextSize(h * 0.10f);
            txt.setTextAlign(Paint.Align.CENTER);
            // Achsenbeschriftung: echte Uhrzeit, nicht Stundenzaehler.
            // Bei 48 Werten wuerde "27h" stehen - das gibt es nicht.
            int step = temps.length > 24 ? 6 : 3;
            int nowHour = java.util.Calendar.getInstance()
                    .get(java.util.Calendar.HOUR_OF_DAY);
            for (int i = 0; i < temps.length; i += step) {
                float x = padL + plotW * i / (temps.length - 1);
                int stunde = (nowHour + i) % 24;
                String label = String.format(java.util.Locale.GERMANY, "%02d", stunde);
                // Mitternacht als Tagesmarke hervorheben
                if (stunde == 0) {
                    txt.setColor(Color.rgb(247, 148, 30));
                    cv.drawText("00", x, h - 8f, txt);
                    txt.setColor(Color.rgb(130, 130, 144));
                } else {
                    cv.drawText(label, x, h - 8f, txt);
                }
            }

            // Regenwahrscheinlichkeit als Balken am unteren Rand
            if (rain != null && rain.length == temps.length) {
                Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
                rp.setColor(Color.argb(110, 120, 178, 255));
                float barMax = plotH * 0.28f;
                for (int i = 0; i < rain.length; i++) {
                    if (rain[i] <= 0) continue;
                    float x = padL + plotW * i / (Math.max(1, temps.length - 1));
                    float bh = barMax * Math.min(100, rain[i]) / 100f;
                    float bw = Math.max(1f, plotW / temps.length * 0.55f);
                    cv.drawRect(x - bw / 2, padT + plotH - bh, x + bw / 2,
                            padT + plotH, rp);
                }
            }

            // Punkt auf dem aktuellen Wert
            // Punkt sitzt jetzt links am Anfang: die Kurve beginnt bei "jetzt"
            int idx = 0;
            float nx = padL + plotW * idx / (temps.length - 1);
            float ny = padT + plotH * (1f - (temps[idx] - min) / span);
            fill.setColor(Ui.ACCENT);
            cv.drawCircle(nx, ny, h * 0.035f, fill);
            fill.setColor(Color.rgb(10, 10, 12));
            cv.drawCircle(nx, ny, h * 0.016f, fill);
        }
    }

    /** Balken fuer Min/Max im Wochenvergleich. */
    static final class RangeBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rf = new android.graphics.RectF();
        private int lo, hi, wMin, wMax;

        RangeBar(android.content.Context c) {
            super(c);
            p.setStyle(Paint.Style.FILL);
        }

        void setRange(int min, int max, Weather.Info wi) {
            lo = min; hi = max;
            wMin = wi.min; wMax = wi.max;
            if (wi.days != null) {
                for (Weather.Day d : wi.days) {
                    wMin = Math.min(wMin, d.min);
                    wMax = Math.max(wMax, d.max);
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            float w = getWidth(), h = getHeight();
            if (w <= 0) return;

            p.setColor(Color.rgb(42, 42, 50));
            rf.set(0, 0, w, h);
            cv.drawRoundRect(rf, h / 2, h / 2, p);

            float span = Math.max(1, wMax - wMin);
            float x1 = w * (lo - wMin) / span;
            float x2 = w * (hi - wMin) / span;
            if (x2 - x1 < h) x2 = x1 + h;
            if (x2 > w) { x2 = w; x1 = Math.max(0, w - h); }

            p.setColor(WeatherPanel.tempColor(hi));
            rf.set(x1, 0, x2, h);
            cv.drawRoundRect(rf, h / 2, h / 2, p);
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (scroll != null) { scroll.smoothScrollBy(0, dp(110)); return true; }
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (scroll != null) { scroll.smoothScrollBy(0, -dp(110)); return true; }
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (scroll != null) { scroll.smoothScrollBy(0, dp(400)); return true; }
        }
        return super.onKeyDown(code, e);
    }
}
