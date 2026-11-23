package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tagesschau-Video: die jeweils neueste "tagesschau in 100 Sekunden".
 *
 * Quelle: offizielle Tagesschau-API (api2u/news), frei zugaenglich, kein Key.
 * Die App holt daraus die neueste Video-Meldung und spielt die MP4 direkt ab -
 * kein Browser, kein Umweg ueber einen Intent-Dialog.
 *
 * Gegengeprueft 11/2026: HTTP 200, content-type video/mp4, Range-Requests ok
 * (Seeking funktioniert), kein DRM.
 */
final class Tagesschau {

    private Tagesschau() { }

    private static final String API = "https://www.tagesschau.de/api2u/news/";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/90 Mobile Safari/537.36";

    private static final long CACHE_MS = 30 * 60 * 1000;
    private static final int CACHE_VER = 2;
    private static final String PREF = "tagesschau";

    /** Ein gefundenes Video. */
    static final class Video {
        String title = "";
        String url = "";        // MP4, direkt abspielbar
        String date = "";       // 23.11.2026
        String time = "";       // 01:47
        String image = "";      // Teaser-Bild
        boolean valid() { return url.length() > 0; }
    }

    private static Video memory;
    private static long memoryAt = 0;
    private static boolean loading = false;

    /** Zwischengespeichertes Video (oder null). */
    static Video cached(Context c) {
        if (memory != null && System.currentTimeMillis() - memoryAt < CACHE_MS) return memory;
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (p.getInt("ver", 0) != CACHE_VER) return null;
        Video v = new Video();
        v.title = p.getString("title", "");
        v.url = p.getString("url", "");
        v.date = p.getString("date", "");
        v.time = p.getString("time", "");
        v.image = p.getString("image", "");
        if (!v.valid()) return null;
        memory = v;
        memoryAt = System.currentTimeMillis();
        return v;
    }

    /** Laedt im Hintergrund, falls aelter als CACHE_MS. */
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
                    Video v = fetch();
                    if (v != null && v.valid()) {
                        p.edit()
                                .putString("title", v.title)
                                .putString("url", v.url)
                                .putString("date", v.date)
                                .putString("time", v.time)
                                .putString("image", v.image)
                                .putLong("ts", System.currentTimeMillis())
                                .putInt("ver", CACHE_VER)
                                .apply();
                        memory = v;
                        memoryAt = System.currentTimeMillis();
                        android.util.Log.i("LEANTS", "Tagesschau: " + v.title + " (" + v.date + ")");
                    }
                } catch (Throwable t) {
                    android.util.Log.w("LEANTS", "Tagesschau fehlgeschlagen: " + t);
                } finally {
                    loading = false;
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    private static Video fetch() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept", "application/json");

        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        conn.disconnect();

        JSONObject root = new JSONObject(sb.toString());
        JSONArray news = root.optJSONArray("news");
        if (news == null) return null;

        // Neueste Video-Meldung nehmen. "100 Sekunden" bevorzugen, sonst das
        // erste Video ueberhaupt - die API listet chronologisch absteigend.
        JSONObject best = null;
        for (int i = 0; i < news.length(); i++) {
            JSONObject n = news.optJSONObject(i);
            if (n == null) continue;
            if (!"video".equals(n.optString("type"))) continue;
            String t = n.optString("title", "");
            if (t.contains("100 Sekunden")) { best = n; break; }
            if (best == null) best = n;
        }
        if (best == null) return null;

        JSONObject streams = best.optJSONObject("streams");
        if (streams == null) return null;

        Video v = new Video();
        v.title = best.optString("title", "tagesschau");
        // Kleinste Variante zuerst: auf dem Stick startet 360p in ~1 s,
        // 540p braucht deutlich laenger (gemessen 6,6 vs. 11,1 MB).
        // Bei einem 100-Sekunden-Nachrichtenblock ist der Unterschied
        // optisch kaum sichtbar, der Start dafuer sofort.
        v.url = streams.optString("h264s", "");
        if (v.url.length() == 0) v.url = streams.optString("h264m", "");
        if (v.url.length() == 0) v.url = streams.optString("h264l", "");
        if (v.url.length() == 0) v.url = streams.optString("h264xl", "");

        String iso = best.optString("date", "");
        // "2026-11-23T01:47:50.831+01:00"
        if (iso.length() >= 16) {
            v.date = iso.substring(8, 10) + "." + iso.substring(5, 7) + "." + iso.substring(0, 4);
            v.time = iso.substring(11, 16);
        }

        JSONObject img = best.optJSONObject("teaserImage");
        if (img != null) {
            // hoechste verfuegbare Variante nehmen
            String[] keys = {"16x9", "16x9_1280", "16x9_960", "1x1_1280"};
            for (String k : keys) {
                String u = img.optString(k, "");
                if (u.length() > 0) { v.image = u; break; }
            }
        }
        return v;
    }
}
