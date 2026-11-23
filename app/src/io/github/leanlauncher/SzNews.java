package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schlagzeilen der Sueddeutschen Zeitung.
 *
 * Quelle: offizieller RSS-Feed rss.sueddeutsche.de/rss/Topthemen
 * (frei zugaenglich, kein Key, kein Login). Geliefert werden Titel, Teaser,
 * Bild-URL, Autor, Datum und der Link auf den ganzen Artikel.
 *
 * Bewusst KEIN Nachbau des Artikeltextes: die App zeigt die Schlagzeile und
 * oeffnet fuer den Volltext die SZ-Seite. Nur so bleibt es sauber.
 *
 * Ergebnis 15 Minuten zwischengespeichert (Prefs + Speicher), damit beim
 * Zeichnen nie Netz noetig ist - dieselbe Regel wie beim Wetter.
 */
final class SzNews {

    private SzNews() { }

    static final String FEED = "https://rss.sueddeutsche.de/rss/Topthemen";

    private static final long CACHE_MS = 15 * 60 * 1000;
    private static final int CACHE_VER = 1;
    private static final String PREF = "sznews";

    static final class Item {
        String title = "";
        String teaser = "";
        String link = "";
        String image = "";
        String author = "";
        String date = "";      // "21.11.2026"
        String category = "";

        boolean valid() { return title.length() > 0 && link.length() > 0; }
    }

    private static List<Item> memoryCache;
    private static long memoryAt = 0;
    private static boolean loading = false;

    /** Zwischengespeicherte Schlagzeilen (leer, wenn noch nie geladen). */
    static List<Item> cached(Context c) {
        if (memoryCache != null && System.currentTimeMillis() - memoryAt < CACHE_MS) {
            return memoryCache;
        }
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (p.getInt("ver", 0) != CACHE_VER) return new ArrayList<Item>();
        String raw = p.getString("raw", "");
        List<Item> list = decode(raw);
        if (!list.isEmpty()) {
            memoryCache = list;
            memoryAt = System.currentTimeMillis();
        }
        return list;
    }

    /** Laedt neu, falls der Stand aelter als CACHE_MS ist. */
    static void refresh(final Context c, final Runnable onDone) {
        if (loading) {
            if (onDone != null) onDone.run();
            return;
        }
        final SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (p.getInt("ver", 0) == CACHE_VER
                && System.currentTimeMillis() - p.getLong("ts", 0) < CACHE_MS) {
            if (onDone != null) onDone.run();
            return;
        }

        loading = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String xml = get(FEED);
                    List<Item> items = parse(xml);
                    if (!items.isEmpty()) {
                        p.edit()
                                .putString("raw", encode(items))
                                .putLong("ts", System.currentTimeMillis())
                                .putInt("ver", CACHE_VER)
                                .apply();
                        memoryCache = items;
                        memoryAt = System.currentTimeMillis();
                        android.util.Log.i("LEANSZ", "SZ ok: " + items.size() + " Schlagzeilen");
                    } else {
                        android.util.Log.w("LEANSZ", "SZ leer - Format geaendert?");
                    }
                } catch (Throwable t) {
                    android.util.Log.w("LEANSZ", "SZ fehlgeschlagen: " + t);
                } finally {
                    loading = false;
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "LeanLauncher/2.0 (+FireTV)");
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");

        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        conn.disconnect();
        return sb.toString();
    }

    /**
     * RSS von Hand zerlegen. Bewusst kein XML-Parser: die SZ liefert HTML in
     * CDATA-Bloecken, das den Systemparser zum Stolpern bringt. Zwei Regex-
     * Durchlaeufe sind hier robuster und deutlich schneller.
     */
    static List<Item> parse(String xml) {
        List<Item> out = new ArrayList<Item>();
        if (xml == null) return out;

        Matcher m = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(xml);
        while (m.find() && out.size() < 20) {
            String block = m.group(1);
            Item it = new Item();
            it.title = text(block, "title");
            it.link = text(block, "link");
            it.author = text(block, "dc:creator");
            it.category = text(block, "category");

            String desc = text(block, "description");
            it.image = firstImage(desc);
            it.teaser = stripHtml(desc);
            it.date = shortDate(text(block, "pubDate"));

            if (it.valid()) out.add(it);
        }
        return out;
    }

    /** Inhalt eines Tags, CDATA und Entities aufgeloest. */
    private static String text(String block, String tag) {
        Matcher m = Pattern.compile("<" + Pattern.quote(tag) + ">(.*?)</" +
                Pattern.quote(tag) + ">", Pattern.DOTALL).matcher(block);
        if (!m.find()) return "";
        return stripCdata(m.group(1)).trim();
    }

    private static String stripCdata(String s) {
        String t = s.trim();
        if (t.startsWith("<![CDATA[")) {
            t = t.substring(9);
            if (t.endsWith("]]>")) t = t.substring(0, t.length() - 3);
            return t.trim();     // CDATA-Inhalt unveraendert lassen (enthaelt HTML)
        }
        return unescape(t);
    }

    /** Erste Bild-URL aus dem HTML-Teaser. */
    private static String firstImage(String html) {
        if (html == null || html.length() == 0) return "";
        Matcher m = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']").matcher(html);
        if (m.find()) return unescape(m.group(1));
        return "";
    }

    /** HTML zu reinem Text: Tags weg, Entities auf, Leerraum normalisieren. */
    static String stripHtml(String html) {
        if (html == null) return "";
        String t = html.replaceAll("<[^>]+>", " ");
        t = unescape(t);
        t = t.replace('\u00a0', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    /** "Sat, 22 Nov 2026 02:40:18 GMT" -> "22.11.2026" */
    private static String shortDate(String rfc) {
        if (rfc == null) return "";
        String[] p = rfc.trim().split("\\s+");
        if (p.length < 4) return "";
        String day = p[1];
        String mon = month(p[2]);
        String year = p[3];
        if (mon.length() == 0) return "";
        return day + "." + mon + "." + year;
    }

    private static String month(String m) {
        String[] names = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
                "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
        String[] keys = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        for (int i = 0; i < keys.length; i++) if (keys[i].equalsIgnoreCase(m)) return names[i];
        return "";
    }

    // ---- Speicherformat: ein Datensatz pro Zeile, Felder mit \u001f getrennt ----

    private static String encode(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) {
            sb.append(clean(i.title)).append('\u001f')
              .append(clean(i.teaser)).append('\u001f')
              .append(clean(i.link)).append('\u001f')
              .append(clean(i.image)).append('\u001f')
              .append(clean(i.author)).append('\u001f')
              .append(clean(i.date)).append('\u001f')
              .append(clean(i.category)).append('\n');
        }
        return sb.toString();
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\u001f', ' ').replace('\u001e', ' ');
    }

    private static List<Item> decode(String raw) {
        List<Item> out = new ArrayList<Item>();
        if (raw == null || raw.length() == 0) return out;
        for (String line : raw.split("\n")) {
            String[] f = line.split("\u001f", -1);
            if (f.length < 7) continue;
            Item i = new Item();
            i.title = f[0];
            i.teaser = f[1];
            i.link = f[2];
            i.image = f[3];
            i.author = f[4];
            i.date = f[5];
            i.category = f[6];
            if (i.valid()) out.add(i);
        }
        return out;
    }
}
