package io.github.leanlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemeinsame Kachel "Nachrichten" mit SZ-Schlagzeilen.
 *
 * Aufbau (eine Kachel, zwei Informationen):
 *   links  - kleines Ressort-Label, Schlagzeile, Teaser, Blatt-Punkte
 *   rechts - Titelbild der aktuellen Schlagzeile
 *   unten  - Fortschrittsbalken: welche von n Schlagzeilen laeuft gerade
 *
 * Die Kachel blaettert alle 8 s selbst weiter. OK oeffnet die Artikelansicht
 * der gerade sichtbaren Schlagzeile. Damit ist sie ohne Zusatzklicks lesbar
 * UND anklickbar - anders als eine reine Textzeile am Bildschirmrand.
 */
final class NewsTile extends FrameLayout {

    private final Context ctx;
    private final float dp;

    private TextView kicker, headline, teaser, dots, badge;
    private ImageView image;
    /** Verlauf ueber dem Bild, damit weisse Schrift immer lesbar bleibt. */
    private View scrim;
    private android.graphics.drawable.GradientDrawable scrimDrawable;


    private List<SzNews.Item> items = new ArrayList<SzNews.Item>();
    private int idx = 0;

    private static final Map<String, Bitmap> IMG = new HashMap<String, Bitmap>();

    private final Runnable tick = new Runnable() {
        public void run() {
            if (items.isEmpty()) return;
            idx = (idx + 1) % items.size();
            bind(items.get(idx));
            postDelayed(this, 8000);
        }
    };

    NewsTile(Context c) {
        super(c);
        this.ctx = c;
        this.dp = c.getResources().getDisplayMetrics().density;
        build();
    }

    private int d(int v) { return (int) (v * dp + 0.5f); }

    private void build() {
        setBackground(Ui.card(dp, 14));
        setClipToOutline(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        // ---- Titelbild als Vollflaeche ----
        image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Runde Ecken: der Inhalt wird per clipToOutline beschnitten, sonst
        // steht das Foto eckig ueber der abgerundeten Kachel.
        addView(image, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Verlauf darueber: links/unten dunkel, damit Text traegt ----
        // Ohne diesen Schleier verschwindet weisse Schrift auf hellen Fotos.
        scrimDrawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2000000, 0xD9000000, 0x66000000, 0x1A000000});
        scrim = new View(ctx);
        scrim.setBackground(scrimDrawable);
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- Text rechtsbuendig ueber dem Bild ----
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.BOTTOM);
        text.setPadding(d(26), d(18), d(26), d(18));

        // Kopf: Badge + Ressort
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        badge = new TextView(ctx);
        badge.setText("SZ");
        badge.setTextColor(Color.rgb(28, 18, 10));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(Ui.ACCENT, dp, 3));
        badge.setPadding(d(6), d(1), d(6), d(1));
        top.addView(badge);

        kicker = new TextView(ctx);
        kicker.setTextColor(Color.rgb(255, 210, 150));
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        kicker.setTypeface(Typeface.DEFAULT_BOLD);
        kicker.setLetterSpacing(0.10f);
        kicker.setSingleLine(true);
        kicker.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        klp.leftMargin = d(10);
        top.addView(kicker, klp);

        text.addView(top);

        // Schlagzeile: deutlich groesser als vorher, max 3 Zeilen
        headline = new TextView(ctx);
        headline.setTextColor(Color.WHITE);
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        headline.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        headline.setMaxLines(3);
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headline.setLineSpacing(d(2), 1f);
        headline.setShadowLayer(6f, 0f, 2f, 0xC0000000);
        headline.setPadding(0, d(10), 0, 0);
        text.addView(headline);

        // Teaser mit Grund, damit er auf jedem Bild lesbar ist
        teaser = new TextView(ctx);
        teaser.setTextColor(Color.rgb(228, 228, 236));
        teaser.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        teaser.setMaxLines(2);
        teaser.setEllipsize(android.text.TextUtils.TruncateAt.END);
        teaser.setLineSpacing(d(2), 1f);
        teaser.setShadowLayer(6f, 0f, 2f, 0xC0000000);
        teaser.setPadding(0, d(7), 0, 0);
        text.addView(teaser);

        dots = new TextView(ctx);
        dots.setTextColor(Color.rgb(190, 190, 200));
        dots.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        dots.setPadding(0, d(8), 0, 0);
        dots.setShadowLayer(4f, 0f, 1f, 0xC0000000);
        text.addView(dots);

        addView(text, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Die Kachel oeffnet die Uebersicht, NICHT direkt einen Artikel:
        // in einer fokussierbaren Kachel sind Inline-Knoepfe per Fernbedienung
        // nicht sauber erreichbar. Alles Einzelne lebt auf der Listenseite.
        setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openList(); }
        });
        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                // 3-dp-Rahmen nach Android-TV-Guideline, statt nur Farbe
                v.setBackground(Ui.focusRing(dp, Color.rgb(18, 18, 22),
                        has ? Ui.ACCENT : Color.rgb(18, 18, 22), 14));
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f)
                        .setDuration(110).start();
            }
        });
    }

    /** Daten setzen. Leere Liste = Ladezustand. */
    void show(List<SzNews.Item> list) {
        items = list == null ? new ArrayList<SzNews.Item>() : list;
        removeCallbacks(tick);
        if (items.isEmpty()) {
            kicker.setText("");
            headline.setText("Schlagzeilen werden geladen …");
            teaser.setText("");
            dots.setText("");
            image.setImageDrawable(null);
            return;
        }
        if (idx >= items.size()) idx = 0;
        bind(items.get(idx));
        postDelayed(tick, 8000);
    }

    private void bind(SzNews.Item it) {
        kicker.setText(it.category.length() > 0
                ? it.category.toUpperCase(java.util.Locale.GERMANY)
                : "SZ TOPTHEMA");
        headline.setText(it.title);
        teaser.setText(it.teaser);

        int total = items.size();
        StringBuilder sb = new StringBuilder();
        sb.append(idx + 1).append(" von ").append(total);
        if (it.date.length() > 0) sb.append("   ·   ").append(it.date);
        dots.setText(sb.toString());

        loadImage(it.image, image);
    }

    /** Oeffnet die Nachrichten-Uebersicht mit allen Schlagzeilen. */
    private void openList() {
        try {
            android.content.Intent i = new android.content.Intent(ctx, NewsListActivity.class);
            HomeService.pauseFor(10 * 60 * 1000);
            ctx.startActivity(i);
        } catch (RuntimeException e) {
            android.util.Log.w("LEANSZ", "Nachrichtenliste fehlgeschlagen: " + e);
        }
    }

    private void openCurrent() {
        if (items.isEmpty()) return;
        SzNews.Item it = items.get(idx);
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
            android.content.Intent i = new android.content.Intent(ctx, ArticleActivity.class);
            i.putExtra(ArticleActivity.EXTRA_TITLE, it.title);
            i.putExtra(ArticleActivity.EXTRA_TEASER, it.teaser);
            i.putExtra(ArticleActivity.EXTRA_LINK, it.link);
            i.putExtra(ArticleActivity.EXTRA_META, meta.toString());
            HomeService.pauseFor(10 * 60 * 1000);
            ctx.startActivity(i);
        } catch (RuntimeException e) {
            android.util.Log.w("LEANSZ", "Artikel-Ansicht fehlgeschlagen: " + e);
        }
    }

    private void loadImage(final String url, final ImageView into) {
        if (url == null || url.length() == 0) {
            into.setImageDrawable(null);
            return;
        }
        Bitmap b = IMG.get(url);
        if (b != null) {
            into.setImageBitmap(b);
            return;
        }
        into.setImageDrawable(null);
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
                    while (o.outWidth / scale > 480) scale *= 2;

                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = scale;
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("User-Agent", "LeanLauncher/2.0");
                    final Bitmap bm = BitmapFactory.decodeStream(c.getInputStream(), null, o2);
                    c.disconnect();
                    if (bm == null) return;
                    IMG.put(url, bm);
                    post(new Runnable() {
                        public void run() { into.setImageBitmap(bm); }
                    });
                } catch (Throwable t) {
                    android.util.Log.w("LEANSZ", "Bild fehlgeschlagen: " + t);
                }
            }
        }).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }
}
