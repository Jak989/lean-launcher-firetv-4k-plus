package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Kurse fuer die Aktien-Kachel.
 *
 * Quelle: Yahoo Finance chart-API (public, kein Key). Ein Aufruf pro Symbol,
 * weil der Multi-Quote-Endpunkt inzwischen einen Cookie/Crumb verlangt - die
 * chart-API bleibt offen und liefert Kurs, Tagesveraenderung und Verlauf.
 *
 * Warum URTH statt "^990100-USD-STRD": der MSCI-World-Index liefert bei Yahoo
 * eine unbrauchbare Prozentzahl (-50 %), der iShares-MSCI-World-ETF (URTH)
 * bildet denselben Index ab und liefert saubere Werte.
 *
 * Kurse werden 10 Minuten gehalten - kein Netz beim Zeichnen.
 */
final class Stocks {

    private Stocks() { }

    private static final String BASE =
            "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/90 Mobile Safari/537.36";

    private static final long CACHE_MS = 10 * 60 * 1000;
    private static final int CACHE_VER = 2;
    private static final String PREF = "stocks";

    /** Ein Kurs. */
    static final class Quote {
        String name = "";      // sprechender Name
        String symbol = "";
        double price = 0;
        double change = 0;     // Prozent
        double open = 0;
        double close = 0;
        double prevClose = 0;      // Vortagesschluss
        double changeAbs = 0;      // Veraenderung absolut
        long marketTime = 0;       // Zeitstempel des Kurses (nicht der Abruf!)
        String currency = "";
        String exchange = "";
        String unit = "";          // Waehrung oder leer
        /** Tagesverlauf fuer die Mini-Kurve (normiert auf 0..1). */
        float[] spark = new float[0];

        boolean up() { return change >= 0; }

        /**
         * Ist der Kurs aktuell? US-Boersen schliessen um 22 Uhr MEZ; danach
         * steht der Kurs still. Ohne diesen Hinweis wirken eingefrorene
         * Kurse wie live - genau der Fehler, den die App vorher hatte.
         */
        String status() {
            if (marketTime <= 0) return "";
            long ageMin = (System.currentTimeMillis() / 1000 - marketTime) / 60;
            if (ageMin < 20) return "live";
            if (ageMin < 60 * 12) {
                // Innerhalb 12 h: Handel beendet, Kurs vom letzten Schluss
                long h = ageMin / 60;
                return h < 1 ? "vor " + ageMin + " min" : "vor " + h + " h";
            }
            return "geschlossen";
        }
    }

    /** Die abgefragten Werte in Anzeigereihenfolge. */
    private static final String[][] WANT = {
            // Name          Yahoo-Symbol   Einheit
            {"MSCI World",   "URTH",        "USD"},
            {"S&P 500",      "^GSPC",       ""},
            {"DAX",          "^GDAXI",      ""},
            {"Nasdaq",       "^IXIC",       ""},
            {"Gold",         "GC=F",        "USD"},
            {"Bitcoin",      "BTC-USD",     "USD"},
            {"EUR/USD",      "EURUSD=X",    ""},
    };

    private static List<Quote> memory;
    private static long memoryAt = 0;
    private static boolean loading = false;

    static List<Quote> cached(Context c) {
        if (memory != null && System.currentTimeMillis() - memoryAt < CACHE_MS) return memory;
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (p.getInt("ver", 0) != CACHE_VER) return new ArrayList<Quote>();
        List<Quote> list = decode(p.getString("raw", ""));
        if (!list.isEmpty()) {
            memory = list;
            memoryAt = System.currentTimeMillis();
        }
        return list;
    }

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
                    List<Quote> out = new ArrayList<Quote>();
                    for (String[] w : WANT) {
                        Quote q = fetch(w[0], w[1], w[2]);
                        if (q != null) out.add(q);
                    }
                    if (!out.isEmpty()) {
                        p.edit()
                                .putString("raw", encode(out))
                                .putLong("ts", System.currentTimeMillis())
                                .putInt("ver", CACHE_VER)
                                .apply();
                        memory = out;
                        memoryAt = System.currentTimeMillis();
                        android.util.Log.i("LEANST", "Kurse geladen: " + out.size());
                    }
                } catch (Throwable t) {
                    android.util.Log.w("LEANST", "Kurse fehlgeschlagen: " + t);
                } finally {
                    loading = false;
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    private static Quote fetch(String name, String symbol, String unit) {
        try {
            String url = BASE + symbol + "?interval=5m&range=1d";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
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
            JSONArray res = root.getJSONObject("chart").optJSONArray("result");
            if (res == null || res.length() == 0) return null;
            JSONObject item = res.getJSONObject(0);
            JSONObject meta = item.optJSONObject("meta");
            if (meta == null) return null;

            Quote q = new Quote();
            q.name = name;
            q.symbol = symbol;
            q.unit = unit;
            q.price = meta.optDouble("regularMarketPrice", 0);
            q.currency = meta.optString("currency", "");
            q.exchange = meta.optString("fullExchangeName", meta.optString("exchangeName", ""));
            q.marketTime = meta.optLong("regularMarketTime", 0);
            double prev = meta.optDouble("chartPreviousClose",
                    meta.optDouble("previousClose", 0));
            q.prevClose = prev;
            if (prev > 0 && q.price > 0) {
                q.change = (q.price - prev) / prev * 100.0;
                q.changeAbs = q.price - prev;
            } else {
                q.change = meta.optDouble("regularMarketChangePercent", 0);
                q.changeAbs = meta.optDouble("regularMarketChange", 0);
            }

            // Tagesverlauf fuer die Mini-Kurve
            JSONObject indicators = item.optJSONObject("indicators");
            if (indicators != null) {
                JSONArray quoteArr = indicators.optJSONArray("quote");
                if (quoteArr != null && quoteArr.length() > 0) {
                    JSONArray closes = quoteArr.getJSONObject(0).optJSONArray("close");
                    if (closes != null && closes.length() > 2) {
                        int n = closes.length();
                        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                        for (int i = 0; i < n; i++) {
                            if (closes.isNull(i)) continue;
                            float v = (float) closes.optDouble(i, 0);
                            if (v < lo) lo = v;
                            if (v > hi) hi = v;
                        }
                        if (hi > lo) {
                            q.spark = new float[n];
                            for (int i = 0; i < n; i++) {
                                if (closes.isNull(i)) {
                                    q.spark[i] = i > 0 ? q.spark[i - 1] : 0.5f;
                                } else {
                                    q.spark[i] = (float) ((closes.optDouble(i, 0) - lo) / (hi - lo));
                                }
                            }
                        }
                    }
                }
            }
            return q.price > 0 ? q : null;
        } catch (Throwable t) {
            android.util.Log.w("LEANST", name + " fehlgeschlagen: " + t);
            return null;
        }
    }

    // ---- Speicherformat ----

    private static String encode(List<Quote> list) {
        StringBuilder sb = new StringBuilder();
        for (Quote q : list) {
            // feste Feldzahl 8: name,symbol,price,change,changeAbs,curr,time,spark
            sb.append(q.name).append('\u001f')
              .append(q.symbol).append('\u001f')
              .append(q.price).append('\u001f')
              .append(q.change).append('\u001f')
              .append(q.changeAbs).append('\u001f')
              .append(q.currency).append('\u001f')
              .append(q.marketTime).append('\u001f');
            for (int i = 0; i < q.spark.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format(java.util.Locale.US, "%.3f", q.spark[i]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static List<Quote> decode(String raw) {
        List<Quote> out = new ArrayList<Quote>();
        if (raw == null || raw.length() == 0) return out;
        for (String line : raw.split("\n")) {
            String[] f = line.split("\u001f", -1);
            if (f.length < 7) continue;
            Quote q = new Quote();
            q.name = f[0];
            q.symbol = f[1];
            try { q.price = Double.parseDouble(f[2]); } catch (RuntimeException e) { continue; }
            try { q.change = Double.parseDouble(f[3]); } catch (RuntimeException e) { }
            try { q.changeAbs = Double.parseDouble(f[4]); } catch (RuntimeException e) { }
            q.currency = f[5];
            try { q.marketTime = Long.parseLong(f[6]); } catch (RuntimeException e) { }
            if (f.length > 7 && f[7].length() > 0) {
                String[] parts = f[7].split(",");
                q.spark = new float[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try { q.spark[i] = Float.parseFloat(parts[i]); } catch (RuntimeException e) { }
                }
            }
            out.add(q);
        }
        return out;
    }
}
