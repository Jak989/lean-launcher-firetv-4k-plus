package io.github.leanlauncher;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Artikel-Ansicht fuer eine SZ-Schlagzeile.
 *
 * Zeigt Titel, Teaser, Autor, Datum und - wenn oeffentlich zugaenglich - den
 * Artikeltext als reinen Text, gross und scrollbar. Hinter einer Paywall bleibt
 * es beim Teaser; dann steht dort ein ehrlicher Hinweis und man kann den
 * Original-Artikel im Browser oeffnen.
 *
 * Bedienung: DPAD hoch/runter scrollt, OK blaettert seitenweise, MENU oeffnet
 * das Original, BACK schliesst.
 */
public class ArticleActivity extends Activity {

    static final String EXTRA_TITLE = "title";
    static final String EXTRA_TEASER = "teaser";
    static final String EXTRA_LINK = "link";
    static final String EXTRA_META = "meta";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private TextView body, statusLine;
    private LinearLayout variant;
    private String link = "";
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        Intent i = getIntent();
        link = i.getStringExtra(EXTRA_LINK);
        if (link == null) link = "";
        buildUi(i.getStringExtra(EXTRA_TITLE), i.getStringExtra(EXTRA_TEASER),
                i.getStringExtra(EXTRA_META));
        loadFull();
    }

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    private void buildUi(String title, String teaser, String meta) {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.bg());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(120), dp(34), dp(120), dp(24));

        TextView head = new TextView(this);
        head.setText("SÜDDEUTSCHE ZEITUNG");
        head.setTextColor(Ui.ACCENT);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.18f);
        page.addView(head);

        TextView t = new TextView(this);
        t.setText(title == null ? "" : title);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setPadding(0, dp(10), 0, 0);
        page.addView(t);

        if (meta != null && meta.length() > 0) {
            TextView m = new TextView(this);
            m.setText(meta);
            m.setTextColor(Ui.TEXT_DIM);
            m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            m.setPadding(0, dp(8), 0, 0);
            page.addView(m);
        }

        if (teaser != null && teaser.length() > 0) {
            TextView te = new TextView(this);
            te.setText(teaser);
            te.setTextColor(Color.rgb(196, 196, 206));
            te.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            te.setLineSpacing(dp(5), 1f);
            te.setPadding(0, dp(14), 0, dp(10));
            page.addView(te);
        }

        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(44, 44, 52));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        rlp.bottomMargin = dp(12);
        page.addView(rule, rlp);

        body = new TextView(this);
        body.setText("");
        body.setTextColor(Color.rgb(224, 224, 232));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        body.setLineSpacing(dp(7), 1f);
        page.addView(body);

        statusLine = new TextView(this);
        statusLine.setText("Text wird geladen …");
        statusLine.setTextColor(Ui.TEXT_DIM);
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusLine.setPadding(dp(2), dp(14), 0, 0);
        page.addView(statusLine);

        // Ersatz-Anzeige, wenn der Volltext nicht abrufbar ist (SZ blockt per
        // Bot-Schutz mit HTTP 403). Ehrlich benannt, statt leere Seite zu zeigen.
        variant = new LinearLayout(this);
        variant.setOrientation(LinearLayout.VERTICAL);
        variant.setVisibility(View.GONE);
        variant.setPadding(0, dp(6), 0, 0);

        TextView vTitle = new TextView(this);
        vTitle.setText("Volltext nicht abrufbar");
        vTitle.setTextColor(Ui.TEXT);
        vTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        variant.addView(vTitle);

        TextView vBody = new TextView(this);
        vBody.setText("Die Süddeutsche schützt ihre Artikel mit einem Bot-Schutz."
                + " Diese App darf die Seite deshalb nicht auslesen.\n\n"
                + "Was du tun kannst:\n"
                + "·  MENU drücken — dann öffnet sich der Original-Artikel im Browser\n"
                + "·  Dort mit deinem SZ-Zugang anmelden, dann ist er vollständig lesbar");
        vBody.setTextColor(Ui.TEXT_DIM);
        vBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        vBody.setLineSpacing(dp(5), 1f);
        vBody.setPadding(0, dp(8), 0, 0);
        variant.addView(vBody);

        TextView vOpen = new TextView(this);
        vOpen.setText("  Artikel im Browser öffnen  ›  ");
        vOpen.setTextColor(Ui.TEXT_ON_FOCUS);
        vOpen.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        vOpen.setBackground(Ui.round(Ui.ACCENT, dp, 8));
        vOpen.setFocusable(true);
        vOpen.setFocusableInTouchMode(true);
        vOpen.setGravity(Gravity.CENTER);
        vOpen.setPadding(dp(14), dp(9), dp(14), dp(9));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(14);
        vOpen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openOriginal(); }
        });
        vOpen.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                v.setBackground(Ui.round(has ? Color.WHITE : Ui.ACCENT, dp, 8));
                ((TextView) v).setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT_ON_FOCUS);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                        .setDuration(100).start();
            }
        });
        variant.addView(vOpen, vlp);
        page.addView(variant);

        TextView hint = new TextView(this);
        hint.setText("MENU  =  Original im Browser öffnen        BACK  =  zurück");
        hint.setTextColor(Color.rgb(100, 100, 112));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(dp(2), dp(18), 0, 0);
        page.addView(hint);

        scroll = new ScrollView(this);
        scroll.addView(page);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
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

    /** Holt die Artikel-Seite und zieht den Absatztext heraus. */
    private void loadFull() {
        if (link.length() == 0) {
            statusLine.setText("Kein Link vorhanden.");
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                final String text = fetchArticle();
                ui.post(new Runnable() {
                    public void run() {
                        if (text != null && text.length() > 200) {
                            body.setText(text);
                            statusLine.setVisibility(View.GONE);
                        } else {
                            variant.setVisibility(View.VISIBLE);
                            statusLine.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    private String fetchArticle() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
            c.setConnectTimeout(9000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/90 Mobile Safari/537.36");
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
            c.setInstanceFollowRedirects(true);

            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    c.getInputStream(), "UTF-8"));
            String line;
            int guard = 0;
            while ((line = r.readLine()) != null && guard++ < 6000) sb.append(line).append(' ');
            r.close();
            c.disconnect();

            String html = sb.toString();
            String out = extractParagraphs(html);
            if (out == null) out = extractJsonBody(html);
            return out;
        } catch (Throwable t) {
            android.util.Log.w("LEANSZ", "Artikel laden fehlgeschlagen: " + t);
            return null;
        }
    }

    /** Alle <p>-Absaetze im article-Bereich zu einem Fliesstext zusammenfassen. */
    private String extractParagraphs(String html) {
        String scope = html;
        int a = html.indexOf("<article");
        if (a >= 0) {
            int e = html.indexOf("</article>", a);
            if (e > a) scope = html.substring(a, e);
        }
        // CSS/JS zuerst weg - sonst landen @media-Bloecke als "Text" im Artikel
        scope = scope.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        scope = scope.replaceAll("(?is)<script[^>]*>.*?</script>", " ");

        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL).matcher(scope);
        while (m.find()) {
            String p = m.group(1);
            // Werbeblocks, Share-Leisten und Buttons aussortieren
            if (p.contains("szshariff") || p.contains("advert") || p.contains("class=\"button")) continue;
            String txt = SzNews.stripHtml(p);
            if (txt.length() < 40) continue;
            // CSS-Reste erkennen: geschweifte Klammern, @media, var(--
            if (looksLikeCss(txt)) continue;
            // Bedienleisten der SZ (Anhoeren/Merken/Teilen/Drucken/Vorlesen)
            if (isToolbarNoise(txt)) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append(txt);
        }
        if (out.length() < 200) return null;
        return out.toString();
    }

    /**
     * Die SZ baut vor jeden Absatz eine Bedienleiste ("Anhören Merken Teilen ...").
     * Solche Zeilen enthalten keinen Nachrichtentext und fliegen raus.
     */
    private static boolean isToolbarNoise(String s) {
        String t = s.toLowerCase(java.util.Locale.GERMANY);
        String[] markers = {"anhören", "merken", "teilen", "feedback", "drucken",
                "vorlesen", "artikel drucken", "bei google bevorzugen"};
        int hits = 0;
        for (String k : markers) if (t.contains(k)) hits++;
        // Erst ab zwei Markern eingreifen - ein echtes Zitat ueber "Teilen"
        // soll nicht verloren gehen.
        if (hits >= 2) return true;

        // Reine Link-/Button-Sammlungen ohne Satzzeichen sind auch keine Prosa
        int letters = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) letters++;
        boolean hasSentence = s.contains(".") || s.contains(",") || s.contains(":");
        return !hasSentence && letters > 0 && s.length() < 120;
    }

    /** Erkennt Stylesheet-Reste, die durch fehlerhaftes HTML in <p> landen. */
    private static boolean looksLikeCss(String s) {
        if (s.contains("@media") || s.contains("var(--")) return true;
        int braces = 0, semis = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') braces++;
            else if (c == ';') semis++;
        }
        return braces >= 2 || (semis > 4 && braces > 0);
    }

    /** Fallback: der Artikeltext steckt auch im JSON-LD (articleBody). */
    private String extractJsonBody(String html) {
        Matcher m = Pattern.compile("\"articleBody\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(html);
        if (!m.find()) return null;
        String raw = m.group(1);
        String txt = raw.replace("\\n", "\n").replace("\\\"", "\"")
                .replace("\\/", "/").replace("\\\\", "\\");
        return txt.length() > 200 ? txt : null;
    }

    private void openOriginal() {
        if (link.length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            HomeService.pauseFor(10 * 60 * 1000);
            startActivity(i);
        } catch (RuntimeException e) {
            android.util.Log.w("LEANSZ", "Kein Browser: " + e);
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            openOriginal();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (scroll != null) { scroll.smoothScrollBy(0, dp(120)); return true; }
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (scroll != null) { scroll.smoothScrollBy(0, -dp(120)); return true; }
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (scroll != null) { scroll.smoothScrollBy(0, dp(420)); return true; }
        }
        return super.onKeyDown(code, e);
    }
}
