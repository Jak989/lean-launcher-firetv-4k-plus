package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nachrichten zu einem Marktwert.
 *
 * Quelle: Google News RSS (kostenlos, kein Key, deutsch). Pro Wert wird eine
 * Suchanfrage gestellt und die neuesten Treffer geholt. Das ist bewusst eine
 * Suchquelle und keine Redaktion - sie liefert aber zu jedem Symbol sofort
 * Kontext, ohne dass fuer jede Aktie ein eigener Feed gepflegt werden muss.
 *
 * Ergebnis 30 Minuten zwischengespeichert (pro Suchbegriff).
 */
final class MarketNews {

    private MarketNews() { }

    private static final String BASE =
            "https://news.google.com/rss/search?hl=de&gl=DE&ceid=DE:de&q=";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/90 Mobile Safari/537.36";

    private static final long CACHE_MS = 30 * 60 * 1000;
    private static final String PREF = "marketnews";

    /** Eine Meldung. */
    static final class Item {
        String title = "";
        String source = "";
        String link = "";
        String age = "";        // "vor 3 Std." o.ae.
        long time = 0;          // Sekunden
        boolean valid() { return title.length() > 0; }
    }

    /** Suchbegriff je Symbol - der Anzeigename taugt oft nicht als Query. */
    static String queryFor(String symbol, String name) {
        if (symbol == null) return name;
        if (symbol.equals("URTH")) return "MSCI World";
        if (symbol.equals("^GDAXI")) return "DAX";
        if (symbol.equals("^GSPC")) return "S&P 500";
        if (symbol.equals("^IXIC")) return "Nasdaq";
        if (symbol.equals("GC=F")) return "Goldpreis";
        if (symbol.equals("BTC-USD")) return "Bitcoin";
        if (symbol.equals("EURUSD=X")) return "Euro Dollar";
        if (symbol.startsWith("^STOXX")) return "EuroStoxx";
        return name;
    }

    /** Holt Meldungen zu einem Wert (Cache first). */
    static List<Item> cached(Context c, String query) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = p.getString("q_" + key(query), "");
        if (raw.length() == 0) return new ArrayList<Item>();
        long ts = p.getLong("t_" + key(query), 0);
        List<Item> list = decode(raw);
        if (System.currentTimeMillis() - ts > CACHE_MS) {
            // abgelaufen: trotzdem liefern, im Hintergrund wird erneuert
        }
        return list;
    }

    /** Laedt neu, falls aelter als CACHE_MS. */
    static void refresh(final Context c, final String query, final Runnable onDone) {
        final SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("t_" + key(query), 0) < CACHE_MS) {
            if (onDone != null) onDone.run();
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    List<Item> list = fetch(query);
                    if (!list.isEmpty()) {
                        p.edit()
                                .putString("q_" + key(query), encode(list))
                                .putLong("t_" + key(query), System.currentTimeMillis())
                                .apply();
                        android.util.Log.i("LEANMN", query + ": " + list.size() + " Meldungen");
                    }
                } catch (Throwable t) {
                    android.util.Log.w("LEANMN", query + " fehlgeschlagen: " + t);
                } finally {
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    private static String key(String q) {
        // SharedPreferences-Schluessel duerfen keine Sonderzeichen enthalten
        return q.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static List<Item> fetch(String query) throws Exception {
        String url = BASE + URLEncoder.encode(query, "UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", UA);

        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        conn.disconnect();

        return parse(sb.toString());
    }

    private static List<Item> parse(String xml) {
        List<Item> out = new ArrayList<Item>();
        if (xml == null) return out;

        Matcher m = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(xml);
        while (m.find() && out.size() < 6) {
            String block = m.group(1);
            Item it = new Item();
            it.title = text(block, "title");
            it.link = text(block, "link");
            it.source = text(block, "source");

            // Google haengt die Quelle mit " - " an den Titel; abschneiden
            if (it.source.length() > 0 && it.title.endsWith(" - " + it.source)) {
                it.title = it.title.substring(0,
                        it.title.length() - it.source.length() - 3);
            }
            String pub = text(block, "pubDate");
            it.time = parseRfc(pub);
            it.age = ageOf(it.time);

            if (it.valid()) out.add(it);
        }
        return out;
    }

    private static String text(String block, String tag) {
        Matcher m = Pattern.compile("<" + Pattern.quote(tag) + ">(.*?)</" +
                Pattern.quote(tag) + ">", Pattern.DOTALL).matcher(block);
        if (!m.find()) return "";
        return unescape(m.group(1).replace("<![CDATA[", "").replace("]]>", "")).trim();
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#39;", "'").replace("&nbsp;", " ");
    }

    private static long parseRfc(String rfc) {
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",
                            java.util.Locale.ENGLISH);
            return f.parse(rfc.trim()).getTime() / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    /** "vor 3 Std." / "vor 12 Min." / "gestern" */
    static String ageOf(long ts) {
        if (ts <= 0) return "";
        long diff = System.currentTimeMillis() / 1000 - ts;
        if (diff < 0) return "";
        long min = diff / 60;
        if (min < 60) return "vor " + Math.max(1, min) + " Min.";
        long h = min / 60;
        if (h < 24) return "vor " + h + " Std.";
        long d = h / 24;
        return d == 1 ? "gestern" : "vor " + d + " Tagen";
    }

    private static String encode(List<Item> list) {
        StringBuilder sb = new StringBuilder();
        for (Item i : list) {
            sb.append(i.title).append('\u001f')
              .append(i.source).append('\u001f')
              .append(i.link).append('\u001f')
              .append(i.time).append('\u001f')
              .append(i.age).append('\n');
        }
        return sb.toString();
    }

    private static List<Item> decode(String raw) {
        List<Item> out = new ArrayList<Item>();
        if (raw == null || raw.length() == 0) return out;
        for (String line : raw.split("\n")) {
            String[] f = line.split("\u001f", -1);
            if (f.length < 5) continue;
            Item i = new Item();
            i.title = f[0];
            i.source = f[1];
            i.link = f[2];
            try { i.time = Long.parseLong(f[3]); } catch (RuntimeException e) { }
            i.age = f[4];
            if (i.valid()) out.add(i);
        }
        return out;
    }
}
