package io.github.leanlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Aktien-Kachel: MSCI World gross, darunter die wichtigsten Maerkte.
 *
 * Aufbau: oben die Tageskurve als breites Diagramm mit Name und Kurs
 * darueber, darunter eine 2x3-Matrix der uebrigen Werte mit je einem
 * Prozentwert.
 *
 * Hoehenrechnung (Kachel 188 dp hoch, Innenabstand 14/12 dp):
 *   Kopflabel "MAeRKTE"      14
 *   Name + Kurs + Kurve      96   (Kurve 88 dp = 44 px, voll ausnutzbar)
 *   Abstand                   4
 *   Matrix 3 Zeilen x 24 dp  72
 *   Summe                   186   (2 dp Luft)
 * Frueher hatte die Matrix Gewichtshoehe (0, 1f) plus starre 26-dp-Zeilen:
 * bei mehr als vier Werten wurden die unteren Zeilen abgeschnitten.
 * Jetzt ist die Hoehe fest und passt zur Kachel.
 */
final class StocksPanel extends LinearLayout {

    private static final int UP = Color.rgb(102, 199, 122);
    private static final int DOWN = Color.rgb(230, 96, 96);

    private final Context ctx;
    private final float dp;

    private TextView headLabel, bigName, bigPrice, bigChange;
    private Spark bigSpark;
    private LinearLayout chartBox;
    private LinearLayout grid;
    private TextView hoverNote;

    StocksPanel(Context c) {
        super(c);
        this.ctx = c;
        this.dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackground(Ui.card(dp, 14));
        setPadding(d(16), d(12), d(16), d(10));

        // Anklickbar: oeffnet die Tagesuebersicht des Aktienmarkts.
        // Wichtig: setClickable(true) explizit - ohne das reagiert eine
        // programmatisch gebaute View trotz setOnClickListener nicht auf OK.
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    HomeService.pauseFor(10 * 60 * 1000);
                    ctx.startActivity(new android.content.Intent(ctx, StocksActivity.class));
                } catch (RuntimeException e) {
                    android.util.Log.w("LEANST", "Markt-Uebersicht fehlgeschlagen: " + e);
                }
            }
        });
        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                // Grundzustand ohne Ring, sonst bleibt der Rahmen haengen
                v.setBackground(has
                        ? Ui.focusRing(dp, Ui.CARD, Ui.ACCENT, 14)
                        : Ui.card(dp, 14));
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                        .setDuration(100).start();
            }
        });

        build();
    }

    private int d(int v) { return (int) (v * dp + 0.5f); }

    private void build() {
        headLabel = new TextView(ctx);
        headLabel.setText("MÄRKTE");
        headLabel.setTextColor(Ui.TEXT_DIM);
        headLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        headLabel.setTypeface(Typeface.DEFAULT_BOLD);
        headLabel.setLetterSpacing(0.16f);
        headLabel.setIncludeFontPadding(false);
        addView(headLabel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(14)));

        // ---- Hauptwert: Name, Kurs, Kurve ----
        LinearLayout nameRow = new LinearLayout(ctx);
        nameRow.setOrientation(HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        bigName = new TextView(ctx);
        bigName.setTextColor(Ui.TEXT);
        bigName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bigName.setIncludeFontPadding(false);
        bigName.setSingleLine(true);
        bigName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameRow.addView(bigName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bigChange = new TextView(ctx);
        bigChange.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bigChange.setTypeface(Typeface.DEFAULT_BOLD);
        bigChange.setSingleLine(true);
        bigChange.setIncludeFontPadding(false);
        nameRow.addView(bigChange);
        addView(nameRow, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(20)));

        bigPrice = new TextView(ctx);
        bigPrice.setTextColor(Ui.TEXT);
        bigPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        bigPrice.setSingleLine(true);
        bigPrice.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        bigPrice.setIncludeFontPadding(false);
        addView(bigPrice, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(36)));

        chartBox = new LinearLayout(ctx);
        chartBox.setOrientation(HORIZONTAL);
        bigSpark = new Spark(ctx);
        chartBox.addView(bigSpark, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, d(38));
        cblp.topMargin = d(2);
        addView(chartBox, cblp);

        // ---- Matrix der uebrigen Werte: 2 Spalten, feste Zeilenhoehe ----
        grid = new LinearLayout(ctx);
        grid.setOrientation(VERTICAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        glp.topMargin = d(4);
        addView(grid, glp);

        hoverNote = new TextView(ctx);
        hoverNote.setTextColor(Ui.TEXT_DIM);
        hoverNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        hoverNote.setIncludeFontPadding(false);
        hoverNote.setVisibility(GONE);
        addView(hoverNote, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(12)));
    }

    /** Daten einsetzen. */
    void show(List<Stocks.Quote> list) {
        grid.removeAllViews();
        if (list == null || list.isEmpty()) {
            bigName.setText("MSCI World");
            bigPrice.setText("–");
            bigChange.setText("");
            return;
        }

        Stocks.Quote first = list.get(0);
        bigName.setText(first.name);
        bigPrice.setText(fmt(first));
        bigChange.setText(signed(first.change) + " %");
        bigChange.setTextColor(first.up() ? UP : DOWN);
        bigSpark.setValues(first.spark, first.up() ? UP : DOWN);

        // Rest in Zweierreihen, max. 6 Werte = 3 Zeilen zu 24 dp (72 dp)
        final int perRow = 3;
        int maxItems = Math.min(list.size(), 7);
        LinearLayout row = null;
        int rows = (maxItems - 1 + perRow - 1) / perRow;
        for (int i = 1; i < maxItems; i++) {
            int idx = i - 1;
            if (idx % perRow == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                // Feste Zeilenhoehe statt Gewicht: 72 dp / 3 Zeilen = 24 dp.
                // Mit Gewicht (0,1f) kollabierten die Zeilen bei knappem
                // Platz - die unteren Werte wurden abgeschnitten.
                int rowH = (rows > 0) ? (int) Math.floor(72f * dp / rows) : d(24);
                grid.addView(row, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, rowH));
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, d(22), 1f);
            clp.rightMargin = (idx % perRow == 0) ? d(10) : 0;
            row.addView(buildCell(list.get(i)), clp);
        }
        // Auffuellen, damit die letzte Zeile die Spaltenbreite haelt
        if (row != null) {
            int missing = perRow - row.getChildCount();
            for (int i = 0; i < missing; i++) {
                row.addView(new View(ctx), new LinearLayout.LayoutParams(0, d(22), 1f));
            }
        }
    }

    /** Eine Zelle: Name links, Prozentwert rechts - beides in einer Zeile. */
    private View buildCell(Stocks.Quote q) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);

        TextView n = new TextView(ctx);
        n.setText(q.name);
        n.setTextColor(Ui.TEXT_DIM);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView ch = new TextView(ctx);
        ch.setText(signed(q.change) + "%");
        ch.setTextColor(q.up() ? UP : DOWN);
        ch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ch.setSingleLine(true);
        c.addView(ch);

        return c;
    }

    /** Kurs kompakt: grosse Zahlen ohne Nachkommastellen, kleine mit. */
    static String fmt(Stocks.Quote q) {
        if (q.price >= 10000) {
            return String.format(Locale.GERMANY, "%,.0f", q.price).replace(',', '.');
        }
        if (q.price >= 100) {
            return String.format(Locale.GERMANY, "%,.2f", q.price);
        }
        return String.format(Locale.GERMANY, "%.4f", q.price);
    }

    static String signed(double v) {
        return String.format(Locale.GERMANY, "%+.2f", v);
    }

    /** Gezeichnete Mini-Kurve eines Tagesverlaufs. */
    static final class Spark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path area = new Path();
        private float[] vals = new float[0];
        private int color = Ui.TEXT_DIM;

        Spark(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, 1.8f * c.getResources()
                    .getDisplayMetrics().density));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
        }

        void setValues(float[] v, int col) {
            this.vals = v == null ? new float[0] : v;
            this.color = col;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (vals.length < 2) return;
            float w = getWidth(), h = getHeight();
            float pad = h * 0.10f;

            p.setColor(color);
            path.reset();
            area.reset();
            for (int i = 0; i < vals.length; i++) {
                float x = w * i / (vals.length - 1);
                float y = pad + (h - 2 * pad) * (1f - vals[i]);
                if (i == 0) {
                    path.moveTo(x, y);
                    area.moveTo(x, h);
                    area.lineTo(x, y);
                } else {
                    path.lineTo(x, y);
                    area.lineTo(x, y);
                }
            }
            area.lineTo(w, h);
            area.close();

            fill.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                    (color & 0x00FFFFFF) | 0x40000000, (color & 0x00FFFFFF) | 0x00000000,
                    android.graphics.Shader.TileMode.CLAMP));
            cv.drawPath(area, fill);
            cv.drawPath(path, p);
        }
    }
}
