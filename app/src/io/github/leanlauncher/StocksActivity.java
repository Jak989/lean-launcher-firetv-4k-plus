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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tagesübersicht Aktienmarkt.
 *
 * Zeigt alle abgefragten Werte als Zeilen: Name, Tagesverlauf als Kurve,
 * Kurs und Veränderung in Prozent. Dazu oben der MSCI World gross.
 *
 * Bedienung: DPAD scrollt, MENU laedt neu, BACK zurueck.
 */
public class StocksActivity extends Activity {

    private static final int UP = Color.rgb(102, 199, 122);
    private static final int DOWN = Color.rgb(230, 96, 96);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private float dp;
    private ScrollView scroll;
    private LinearLayout listCol;
    private TextView bigPrice, bigChange, bigName, stamp, statusLine;
    private LinearLayout newsCol;
    private TextView newsTopic, newsHint;
    private String shownQuery = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        buildUi();
        load();
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
        page.setPadding(dp(48), dp(20), dp(48), dp(22));

        // Kopf
        TextView head = new TextView(this);
        head.setText("MÄRKTE  ·  HEUTE");
        head.setTextColor(Ui.ACCENT);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.18f);
        page.addView(head);

        // MSCI World gross
        LinearLayout bigRow = new LinearLayout(this);
        bigRow.setOrientation(LinearLayout.HORIZONTAL);
        bigRow.setGravity(Gravity.CENTER_VERTICAL);
        bigRow.setPadding(0, dp(6), 0, dp(10));

        LinearLayout bigLeft = new LinearLayout(this);
        bigLeft.setOrientation(LinearLayout.VERTICAL);

        bigName = new TextView(this);
        bigName.setText("MSCI World");
        bigName.setTextColor(Ui.TEXT_DIM);
        bigName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bigLeft.addView(bigName);

        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.BOTTOM);

        bigPrice = new TextView(this);
        bigPrice.setText("–");
        bigPrice.setTextColor(Ui.TEXT);
        bigPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        bigPrice.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        bigPrice.setIncludeFontPadding(false);
        priceRow.addView(bigPrice);

        bigChange = new TextView(this);
        bigChange.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams bclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bclp.leftMargin = dp(12);
        bclp.bottomMargin = dp(6);
        priceRow.addView(bigChange, bclp);

        bigLeft.addView(priceRow);
        bigRow.addView(bigLeft, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        stamp = new TextView(this);
        stamp.setTextColor(Color.rgb(104, 104, 118));
        stamp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        stamp.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        bigRow.addView(stamp);

        page.addView(bigRow);

        // Trennlinie
        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(38, 38, 44));
        page.addView(rule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        // Spaltenkoepfe
        LinearLayout colHead = new LinearLayout(this);
        colHead.setOrientation(LinearLayout.HORIZONTAL);
        colHead.setPadding(dp(4), dp(8), dp(4), dp(4));
        addLabel(colHead, "WERT", 0, 1f, Color.rgb(104, 104, 118));
        addLabel(colHead, "VERLAUF", dp(150), 0f, Color.rgb(104, 104, 118));
        addLabel(colHead, "KURS", dp(120), 0f, Color.rgb(104, 104, 118));
        addLabel(colHead, "HEUTE", dp(90), 0f, Color.rgb(104, 104, 118));
        page.addView(colHead);

        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        page.addView(listCol);

        statusLine = new TextView(this);
        statusLine.setText("Kurse werden geladen …");
        statusLine.setTextColor(Ui.TEXT_DIM);
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusLine.setPadding(dp(4), dp(14), 0, 0);
        page.addView(statusLine);

        // ---- Nachrichten zum gewaehlten Wert ----
        TextView newsHead = new TextView(this);
        newsHead.setText("MELDUNGEN");
        newsHead.setTextColor(Ui.TEXT_DIM);
        newsHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        newsHead.setTypeface(Typeface.DEFAULT_BOLD);
        newsHead.setLetterSpacing(0.16f);
        newsHead.setPadding(dp(4), dp(18), 0, dp(4));
        page.addView(newsHead);

        newsTopic = new TextView(this);
        newsTopic.setTextColor(Ui.ACCENT);
        newsTopic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        newsTopic.setPadding(dp(4), 0, 0, dp(6));
        page.addView(newsTopic);

        newsCol = new LinearLayout(this);
        newsCol.setOrientation(LinearLayout.VERTICAL);
        page.addView(newsCol);

        newsHint = new TextView(this);
        newsHint.setText("Wert auswählen (▲▼), dann erscheinen hier die Meldungen dazu.");
        newsHint.setTextColor(Color.rgb(120, 120, 134));
        newsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        newsHint.setPadding(dp(4), dp(6), 0, dp(4));
        newsCol.addView(newsHint);

        TextView hint = new TextView(this);
        hint.setText("MENU  =  Kurse neu laden        BACK  =  zurück");
        hint.setTextColor(Color.rgb(96, 96, 110));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(dp(4), dp(16), 0, 0);
        page.addView(hint);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void addLabel(LinearLayout row, String text, int width, float weight, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.10f);
        if (width > 0) {
            row.addView(t, new LinearLayout.LayoutParams(width,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            row.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        }
    }

    private void load() {
        List<Stocks.Quote> cached = Stocks.cached(this);
        if (!cached.isEmpty()) {
            fill(cached);
            statusLine.setVisibility(View.GONE);
        }
        Stocks.refresh(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        List<Stocks.Quote> fresh = Stocks.cached(StocksActivity.this);
                        if (!fresh.isEmpty()) {
                            fill(fresh);
                            statusLine.setVisibility(View.GONE);
                        } else if (listCol.getChildCount() == 0) {
                            statusLine.setText("Kurse gerade nicht abrufbar.");
                        }
                    }
                });
            }
        });
    }

    private void fill(List<Stocks.Quote> list) {
        if (list.isEmpty()) return;
        Stocks.Quote first = list.get(0);
        bigName.setText(first.name);
        bigPrice.setText(StocksPanel.fmt(first));
        String cur = first.currency.length() > 0 ? " " + first.currency : "";
        bigPrice.setText(StocksPanel.fmt(first) + cur);
        // Absolute Veraenderung UND Prozent - Profis zeigen beides
        bigChange.setText(String.format(Locale.GERMANY, "%+.2f", first.changeAbs)
                + "  (" + StocksPanel.signed(first.change) + " %)");
        bigChange.setTextColor(first.up() ? UP : DOWN);
        // ECHTE Kurszeit aus der API, nicht die Abrufzeit. Nachts stehen
        // US-Kurse still - ohne diesen Hinweis wirken sie wie live.
        if (first.marketTime > 0) {
            stamp.setText("Kurs " + new java.text.SimpleDateFormat("HH:mm",
                    Locale.GERMANY).format(new java.util.Date(first.marketTime * 1000))
                    + "  ·  " + first.status());
        } else {
            stamp.setText(first.status());
        }

        listCol.removeAllViews();
        // Ersten Wert ueberspringen - der steht schon gross oben
        for (int i = 1; i < list.size(); i++) {
            listCol.addView(buildRow(list.get(i)));
        }
        // Meldungen zum Hauptwert sofort zeigen
        if (newsCol != null && newsCol.getChildCount() <= 1) {
            loadNewsFor(first);
        }
    }

    private View buildRow(final Stocks.Quote q) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(11), dp(10), dp(11));
        // Fokussierbar: beim Durchscrollen erscheinen unten die Meldungen
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(has
                        ? Ui.round(Color.rgb(30, 30, 36), dp, 8)
                        : null);
                v.animate().scaleX(has ? 1.01f : 1f).scaleY(has ? 1.01f : 1f)
                        .setDuration(90).start();
                if (has) loadNewsFor(q);
            }
        });

        TextView n = new TextView(this);
        n.setText(q.name);
        n.setTextColor(Ui.TEXT);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        n.setSingleLine(true);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        StocksPanel.Spark sp = new StocksPanel.Spark(this);
        row.addView(sp, new LinearLayout.LayoutParams(dp(150), dp(30)));
        sp.setValues(q.spark, q.up() ? UP : DOWN);

        TextView p = new TextView(this);
        String c = q.currency.length() > 0 && !q.currency.equals("USD") ? " " + q.currency : "";
        p.setText(StocksPanel.fmt(q) + c);
        p.setTextColor(Ui.TEXT);
        p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        p.setGravity(Gravity.RIGHT);
        p.setSingleLine(true);
        row.addView(p, new LinearLayout.LayoutParams(dp(120),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ch = new TextView(this);
        ch.setText(StocksPanel.signed(q.change) + " %");
        ch.setTextColor(q.up() ? UP : DOWN);
        ch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ch.setGravity(Gravity.RIGHT);
        ch.setSingleLine(true);
        row.addView(ch, new LinearLayout.LayoutParams(dp(90),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return row;
    }

    /** Nachrichten zum fokussierten Wert laden und anzeigen. */
    private void loadNewsFor(final Stocks.Quote q) {
        final String query = MarketNews.queryFor(q.symbol, q.name);
        if (query.equals(shownQuery)) return;      // schon sichtbar
        shownQuery = query;

        newsTopic.setText(query + "  ·  aktuelle Meldungen");
        newsCol.removeAllViews();
        newsHint = new TextView(this);
        newsHint.setText("Meldungen werden geladen …");
        newsHint.setTextColor(Color.rgb(120, 120, 134));
        newsHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        newsHint.setPadding(dp(4), dp(6), 0, dp(4));
        newsCol.addView(newsHint);

        java.util.List<MarketNews.Item> cached = MarketNews.cached(this, query);
        if (!cached.isEmpty()) renderNews(cached);

        MarketNews.refresh(this, query, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        if (!query.equals(shownQuery)) return;
                        java.util.List<MarketNews.Item> fresh =
                                MarketNews.cached(StocksActivity.this, query);
                        if (!fresh.isEmpty()) renderNews(fresh);
                        else if (newsCol.getChildCount() <= 1) {
                            newsHint.setText("Keine aktuellen Meldungen gefunden.");
                        }
                    }
                });
            }
        });
    }

    private void renderNews(java.util.List<MarketNews.Item> list) {
        newsCol.removeAllViews();
        int n = Math.min(list.size(), 4);
        for (int i = 0; i < n; i++) {
            MarketNews.Item it = list.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(4), dp(5), dp(4), dp(5));

            TextView t = new TextView(this);
            t.setText("·  " + it.title);
            t.setTextColor(Color.rgb(214, 214, 224));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setLineSpacing(dp(2), 1f);
            row.addView(t);

            String meta = it.source.length() > 0 ? it.source : "Google News";
            if (it.age.length() > 0) meta += "   ·   " + it.age;
            TextView m = new TextView(this);
            m.setText(meta);
            m.setTextColor(Color.rgb(120, 120, 134));
            m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            m.setPadding(0, dp(2), 0, 0);
            row.addView(m);

            newsCol.addView(row);
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            // Cache verwerfen und neu laden
            Stocks.refresh(this, new Runnable() {
                public void run() {
                    ui.post(new Runnable() {
                        public void run() { fill(Stocks.cached(StocksActivity.this)); }
                    });
                }
            });
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (scroll != null) { scroll.smoothScrollBy(0, dp(110)); return true; }
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (scroll != null) { scroll.smoothScrollBy(0, -dp(110)); return true; }
        }
        return super.onKeyDown(code, e);
    }
}
