package io.github.leanlauncher;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Nachrichten-Podcasts auf Abruf (kein Live-Radio).
 *
 * Quelle: ARD-Audiothek GraphQL (api.ardaudiothek.de). Liefert pro Sendung die
 * neuesten Folgen mit direkter MP3-URL - kein DRM, kein Login.
 *
 * Ergebnisse werden 1 h im Speicher gehalten; die App laedt sie beim Oeffnen
 * der Nachrichten-Ansicht im Hintergrund.
 */
final class Podcasts {

    private Podcasts() { }

    static final String[] SHOW_TERMS = {
            "tagesschau 100 Sekunden",
            "Tagesschau (Audio-Podcast)",
            "Deutschlandfunk nachrichten",
            "Tagesthemen audio",
    };

    static final String[] SHOW_LABEL = {
            "Tagesschau in 100 Sekunden",
            "Tagesschau (Audio)",
            "Deutschlandfunk",
            "Tagesthemen",
    };

    static final class Folge {
        String showLabel;
        String title;
        String date;       // yyyy-MM-dd
        int durationSec;
        String url;
    }

    private static List<Folge> cache;
    private static long cachedAt = 0;
    private static final long TTL = 60 * 60 * 1000;
    private static boolean loading = false;

    private static final String GQL = "https://api.ardaudiothek.de/graphql";
    private static final String QUERY =
            "query($q: String!) { search(query: $q, limit: 1) { programSets { nodes {"
                    + " title items(first: 5) { nodes { title publishDate duration audios { url } } } } } } }";

    /** Liefert den Cache (ggf. leer) und startet bei Bedarf einen Ladevorgang. */
    static synchronized List<Folge> get(List<Folge> out, Runnable onDone) {
        if (cache != null && System.currentTimeMillis() - cachedAt < TTL) {
            out.clear();
            out.addAll(cache);
            return out;
        }
        if (!loading) {
            loading = true;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        List<Folge> fresh = fetchAll();
                        if (!fresh.isEmpty()) {
                            cache = fresh;
                            cachedAt = System.currentTimeMillis();
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        loading = false;
                    }
                    if (onDone != null) new Handler(Looper.getMainLooper()).post(onDone);
                }
            }).start();
        }
        if (cache != null) {
            out.clear();
            out.addAll(cache);
        }
        return out;
    }

    private static List<Folge> fetchAll() {
        List<Folge> all = new ArrayList<Folge>();
        for (int i = 0; i < SHOW_TERMS.length; i++) {
            try {
                List<Folge> part = fetchOne(SHOW_TERMS[i]);
                for (Folge f : part) {
                    f.showLabel = SHOW_LABEL[i];
                    all.add(f);
                }
            } catch (Throwable ignored) { }
        }
        return all;
    }

    private static List<Folge> fetchOne(String term) throws Exception {
        List<Folge> res = new ArrayList<Folge>();

        JSONObject vars = new JSONObject();
        vars.put("q", term);
        JSONObject body = new JSONObject();
        body.put("query", QUERY);
        body.put("variables", vars);

        HttpURLConnection c = (HttpURLConnection) new URL(GQL).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "okhttp/4.12.0");

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        c.disconnect();

        JSONObject root = new JSONObject(sb.toString());
        JSONObject search = root.getJSONObject("data").getJSONObject("search");
        JSONArray sets = search.getJSONObject("programSets").getJSONArray("nodes");
        if (sets.length() == 0) return res;

        JSONObject set = sets.getJSONObject(0);
        JSONArray items = set.getJSONObject("items").getJSONArray("nodes");
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            String url = null;
            JSONArray audios = it.optJSONArray("audios");
            if (audios != null && audios.length() > 0) {
                url = audios.getJSONObject(0).optString("url", null);
            }
            if (url == null || url.length() == 0) continue;

            Folge f = new Folge();
            f.title = it.optString("title", "");
            f.date = it.optString("publishDate", "");
            if (f.date.length() > 10) f.date = f.date.substring(0, 10);
            f.durationSec = it.optInt("duration", 0);
            f.url = url;
            res.add(f);
        }
        return res;
    }
}
