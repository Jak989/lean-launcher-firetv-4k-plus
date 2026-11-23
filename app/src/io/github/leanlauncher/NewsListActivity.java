package io.github.leanlauncher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nachrichten-Uebersicht: alle SZ-Schlagzeilen als Liste.
 *
 * Aufbau wie bei etablierten TV-Apps: links die Liste (Titel, Ressort, Datum),
 * rechts eine Vorschau mit Titelbild, Teaser und Zeit. Fokus wandert mit dem
 * D-Pad durch die Liste, die Vorschau zieht jeweils nach.
 *
 * OK      oeffnet den Artikel im Lesemodus
 * MENU    oeffnet tagesschau.de im Browser
 * BACK    zurueck zum Startbildschirm
 */
public class NewsListActivity extends Activity {

    private LinearLayout listCol;
    private LinearLayout previewBox;
    private ImageView previewImage;
    private TextView previewKicker, previewTitle, previewTeaser, previewMeta;
    private TextView statusLine;
    private ScrollView listScroll;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private float dp;

    private final List<SzNews.Item> items = new ArrayList<SzNews.Item>();
    private final List<View> rows = new ArrayList<View>();
    private final Map<String, Bitmap> imgCache = new HashMap<String, Bitmap>();
    private int selected = 0;

    private static final String TAGESSCHAU = "https://www.tagesschau.de/";

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

    // ---------------- Aufbau ----------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(20), dp(48), dp(20));

        // ---- Kopfzeile ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("NACHRICHTEN");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setLetterSpacing(0.10f);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));


        page.addView(head);

        TextView hint = new TextView(this);
        hint.setText("SZ Topthemen  ·  OK liest den Artikel");
        hint.setTextColor(Color.rgb(104, 104, 118));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(0, dp(8), 0, dp(12));
        page.addView(hint);

        // ---- Inhalt: Liste links, Vorschau rechts ----
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(listCol);
        body.addView(listScroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.25f));

        // Vorschau
        LinearLayout pv = new LinearLayout(this);
        pv.setOrientation(LinearLayout.VERTICAL);
        pv.setBackground(Ui.card(dp, 14));
        pv.setPadding(dp(16), dp(14), dp(16), dp(14));

        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setBackgroundColor(Color.rgb(32, 32, 38));
        pv.addView(previewImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        previewKicker = new TextView(this);
        previewKicker.setTextColor(Ui.ACCENT);
        previewKicker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        previewKicker.setPadding(0, dp(10), 0, 0);
        pv.addView(previewKicker);

        previewTitle = new TextView(this);
        previewTitle.setTextColor(Ui.TEXT);
        previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        previewTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        previewTitle.setMaxLines(3);
        previewTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previewTitle.setPadding(0, dp(4), 0, 0);
        pv.addView(previewTitle);

        previewTeaser = new TextView(this);
        previewTeaser.setTextColor(Ui.TEXT_DIM);
        previewTeaser.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        previewTeaser.setMaxLines(3);
        previewTeaser.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previewTeaser.setLineSpacing(dp(2), 1f);
        previewTeaser.setPadding(0, dp(7), 0, 0);
        pv.addView(previewTeaser);

        previewMeta = new TextView(this);
        previewMeta.setTextColor(Color.rgb(104, 104, 118));
        previewMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        previewMeta.setPadding(0, dp(8), 0, 0);
        pv.addView(previewMeta);

        LinearLayout.LayoutParams pvlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        pvlp.leftMargin = dp(14);
        body.addView(pv, pvlp);

        page.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusLine = new TextView(this);
        statusLine.setText("Schlagzeilen werden geladen …");
        statusLine.setTextColor(Ui.TEXT_DIM);
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusLine.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER;
        root.addView(statusLine, slp);

        setContentView(root);
    }

    // ---------------- Daten ----------------

    private void load() {
        List<SzNews.Item> cached = SzNews.cached(this);
        if (!cached.isEmpty()) {
            fill(cached);
            statusLine.setVisibility(View.GONE);
        }
        SzNews.refresh(this, new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() {
                        List<SzNews.Item> fresh = SzNews.cached(NewsListActivity.this);
                        if (!fresh.isEmpty()) {
                            fill(fresh);
                            statusLine.setVisibility(View.GONE);
                        } else if (items.isEmpty()) {
                            statusLine.setText("Keine Schlagzeilen verfügbar.");
                        }
                    }
                });
            }
        });
    }

    private void fill(List<SzNews.Item> list) {
        items.clear();
        items.addAll(list);
        rows.clear();
        listCol.removeAllViews();

        for (int i = 0; i < items.size(); i++) {
            View row = buildRow(items.get(i), i);
            rows.add(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            listCol.addView(row, lp);
        }

        if (!rows.isEmpty()) {
            selected = 0;
            rows.get(0).requestFocus();
            showPreview(0);
        }
    }

    /** Ein Listeneintrag: Ressort + Titel + Datum. */
    private View buildRow(final SzNews.Item it, final int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setBackground(Ui.round(Ui.CARD, dp, 8));

        View bar = new View(this);
        bar.setBackgroundColor(Ui.ACCENT);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(3),
                ViewGroup.LayoutParams.MATCH_PARENT);
        blp.rightMargin = dp(10);
        blp.topMargin = dp(2);
        blp.bottomMargin = dp(2);
        row.addView(bar, blp);
        bar.setVisibility(View.INVISIBLE);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(it.title);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        txt.addView(t);

        String meta = (it.category.length() > 0 ? it.category.toUpperCase(Locale.GERMANY) : "SZ")
                + (it.date.length() > 0 ? "   ·   " + it.date : "");
        TextView m = new TextView(this);
        m.setText(meta);
        m.setTextColor(Color.rgb(112, 112, 126));
        m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        m.setPadding(0, dp(3), 0, 0);
        txt.addView(m);

        row.addView(txt, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final View barRef = bar;
        final TextView titleRef = t;
        final TextView metaRef = m;
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(Ui.round(has ? Ui.CARD_FOCUS : Ui.CARD, dp, 8));
                titleRef.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
                metaRef.setTextColor(has ? Color.rgb(90, 66, 34) : Color.rgb(112, 112, 126));
                barRef.setVisibility(has ? View.INVISIBLE : View.INVISIBLE);
                if (has) {
                    selected = index;
                    showPreview(index);
                    scrollTo(row);
                }
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openArticle(it); }
        });
        return row;
    }

    /** Haelt den fokussierten Eintrag in der Mitte der Liste. */
    private void scrollTo(View row) {
        if (listScroll == null) return;
        int y = row.getTop() - (listScroll.getHeight() - row.getHeight()) / 2;
        listScroll.smoothScrollTo(0, Math.max(0, y));
    }

    private void showPreview(int index) {
        if (index < 0 || index >= items.size()) return;
        final SzNews.Item it = items.get(index);
        previewKicker.setText(it.category.length() > 0
                ? it.category.toUpperCase(Locale.GERMANY) : "SZ TOPTHEMA");
        previewTitle.setText(it.title);
        previewTeaser.setText(it.teaser);

        StringBuilder meta = new StringBuilder();
        if (it.author.length() > 0) meta.append(it.author);
        if (it.date.length() > 0) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(it.date);
        }
        meta.append("   ·   ").append(index + 1).append(" von ").append(items.size());
        previewMeta.setText(meta.toString());

        loadImage(it.image);
    }

    private void loadImage(final String url) {
        if (url == null || url.length() == 0) {
            previewImage.setImageDrawable(null);
            return;
        }
        Bitmap b = imgCache.get(url);
        if (b != null) {
            previewImage.setImageBitmap(b);
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    HttpURLConnection c0 = (HttpURLConnection) new URL(url).openConnection();
                    c0.setConnectTimeout(8000);
                    c0.setRequestProperty("User-Agent", "LeanLauncher/2.0");
                    InputStream i0 = c0.getInputStream();
                    BitmapFactory.decodeStream(i0, null, o);
                    i0.close();
                    c0.disconnect();

                    int scale = 1;
                    while (o.outWidth / scale > 520) scale *= 2;

                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = scale;
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("User-Agent", "LeanLauncher/2.0");
                    final Bitmap bm = BitmapFactory.decodeStream(c.getInputStream(), null, o2);
                    c.disconnect();
                    if (bm == null) return;
                    imgCache.put(url, bm);
                    ui.post(new Runnable() {
                        public void run() {
                            if (items.size() > selected
                                    && url.equals(items.get(selected).image)) {
                                previewImage.setImageBitmap(bm);
                            }
                        }
                    });
                } catch (Throwable ignored) { }
            }
        }).start();
    }

    private void openArticle(SzNews.Item it) {
        if (it == null || it.link.length() == 0) return;
        try {
            StringBuilder meta = new StringBuilder();
            if (it.author.length() > 0) meta.append(it.author);
            if (it.date.length() > 0) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(it.date);
            }
            if (it.category.length() > 0) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(it.category);
            }
            android.content.Intent i = new android.content.Intent(this, ArticleActivity.class);
            i.putExtra(ArticleActivity.EXTRA_TITLE, it.title);
            i.putExtra(ArticleActivity.EXTRA_TEASER, it.teaser);
            i.putExtra(ArticleActivity.EXTRA_LINK, it.link);
            i.putExtra(ArticleActivity.EXTRA_META, meta.toString());
            startActivity(i);
        } catch (RuntimeException e) {
            android.util.Log.w("LEANSZ", "Artikel fehlgeschlagen: " + e);
        }
    }

    /** Startet das neueste Tagesschau-Video direkt im Player. */
    private void openTagesschau() {
        try {
            startActivity(new android.content.Intent(this, TagesschauActivity.class));
        } catch (RuntimeException e) {
            android.util.Log.w("LEANTS", "Tagesschau-Video fehlgeschlagen: " + e);
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            openTagesschau();
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
