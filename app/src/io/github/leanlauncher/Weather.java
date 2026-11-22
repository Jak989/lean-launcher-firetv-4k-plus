package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Wetter fuer Muenchen ueber Open-Meteo (kostenlos, kein API-Key, keine Registrierung).
 *
 * Ergebnis wird 30 Minuten zwischengespeichert - kein Netzwerk beim Zeichnen.
 */
final class Weather {

    private Weather() { }

    /**
     * Standort ist konfigurierbar - bewusst NICHT fest eingebaut.
     * Wer die App nutzt, traegt hier die eigenen Koordinaten ein.
     * Standard: Muenchen (allgemein, kein Wohnort).
     */
    static final double LAT = 48.137;    // Breitengrad
    static final double LON = 11.575;    // Laengengrad
    static final String CITY = "München";

    private static final String URL =
            "https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
                    + "&current=temperature_2m,weather_code&timezone=Europe%2FBerlin";

    private static final long CACHE_MS = 30 * 60 * 1000;
    private static final String PREF = "weather";

    /** Liefert z.B. "3° Nebel" - aus Cache, sonst leer. */
    static String cached(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("ts", 0) > CACHE_MS) return p.getString("txt", "");
        return p.getString("txt", "");
    }

    /** Aktualisiert im Hintergrund, falls aelter als 30 Minuten. */
    static void refresh(final Context c, final Runnable onDone) {
        final SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("ts", 0) < CACHE_MS) return;

        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(URL).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "LeanLauncher/2.0");

                    StringBuilder sb = new StringBuilder();
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    conn.disconnect();

                    JSONObject o = new JSONObject(sb.toString()).getJSONObject("current");
                    double temp = o.getDouble("temperature_2m");
                    int code = o.getInt("weather_code");

                    String txt = Math.round(temp) + "° " + symbol(code);
                    p.edit()
                            .putString("txt", txt)
                            .putLong("ts", System.currentTimeMillis())
                            .apply();
                } catch (Throwable ignored) {
                } finally {
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    /** WMO-Wettercode -> kurzes Symbol/Wort. */
    private static String symbol(int c) {
        if (c == 0) return "☀︎ klar";
        if (c == 1) return "☀︎ heiter";
        if (c == 2) return "⛅ wolkig";
        if (c == 3) return "☁︎ bedeckt";
        if (c == 45 || c == 48) return "≡ Nebel";
        if (c >= 51 && c <= 57) return "☂ Sprühregen";
        if (c >= 61 && c <= 67) return "☂ Regen";
        if (c >= 71 && c <= 77) return "❄ Schnee";
        if (c >= 80 && c <= 82) return "☂ Schauer";
        if (c >= 85 && c <= 86) return "❄ Schneeschauer";
        if (c >= 95) return "⚡ Gewitter";
        return "";
    }
}
