package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Wetter ueber Open-Meteo (kostenlos, kein API-Key, keine Registrierung).
 *
 * Liefert:
 *   - aktuelles Wetter mit Symbol
 *   - gefuehlte Temperatur
 *   - Tages-Minimum und -Maximum
 *   - Luftfeuchte und Wind
 *
 * Ergebnis wird 20 Minuten zwischengespeichert - kein Netzwerk beim Zeichnen.
 */
final class Weather {

    private Weather() { }

    /**
     * Standort ist konfigurierbar - bewusst NICHT fest eingebaut.
     * Wer die App nutzt, traegt hier die eigenen Koordinaten ein.
     * Standard: Muenchen (allgemein, kein Wohnort).
     */
    static final double LAT = 48.137;
    static final double LON = 11.575;
    static final String CITY = "München";

    private static final String URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + LAT + "&longitude=" + LON
            + "&current=temperature_2m,weather_code,relative_humidity_2m,"
            +   "apparent_temperature,wind_speed_10m"
            + "&daily=temperature_2m_max,temperature_2m_min"
            + "&timezone=Europe%2FBerlin&forecast_days=1";

    private static final long CACHE_MS = 20 * 60 * 1000;
    private static final String PREF = "weather";

    /** Ein Datenpunkt. */
    static final class Info {
        int temp = 0;
        int feels = 0;
        int min = 0;
        int max = 0;
        int humidity = 0;
        int wind = 0;
        int code = -1;
        String symbol = "";
        String label = "";
        long stamp = 0;

        boolean valid() { return code >= 0; }

        /** "1° Nebel · 0° / 4°" */
        String line1() {
            if (!valid()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append(symbol).append(' ').append(temp).append("°");
            if (label.length() > 0) sb.append(' ').append(label);
            return sb.toString();
        }

        /** "gefühlt -1° · 0° / 4° · 97 % · 2 km/h" */
        String line2() {
            if (!valid()) return "";
            return "gefühlt " + feels + "°  ·  " + min + "° / " + max + "°"
                    + "  ·  " + humidity + " %  ·  " + wind + " km/h";
        }
    }

    private static Info cachedInfo;

    /** Liefert das zwischengespeicherte Wetter (oder ein leeres Info). */
    static Info cached(Context c) {
        if (cachedInfo != null && System.currentTimeMillis() - cachedInfo.stamp < CACHE_MS) {
            return cachedInfo;
        }
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Info i = new Info();
        i.temp = p.getInt("temp", 0);
        i.feels = p.getInt("feels", 0);
        i.min = p.getInt("min", 0);
        i.max = p.getInt("max", 0);
        i.humidity = p.getInt("humidity", 0);
        i.wind = p.getInt("wind", 0);
        i.code = p.getInt("code", -1);
        i.symbol = p.getString("symbol", "");
        i.label = p.getString("label", "");
        i.stamp = p.getLong("ts", 0);
        cachedInfo = i;
        return i;
    }

    /** Aktualisiert im Hintergrund, falls aelter als 20 Minuten. */
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

                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject cur = root.getJSONObject("current");
                    JSONObject daily = root.getJSONObject("daily");

                    int code = cur.getInt("weather_code");
                    Info i = new Info();
                    i.temp = (int) Math.round(cur.getDouble("temperature_2m"));
                    i.feels = (int) Math.round(cur.getDouble("apparent_temperature"));
                    i.humidity = cur.optInt("relative_humidity_2m", 0);
                    i.wind = (int) Math.round(cur.optDouble("wind_speed_10m", 0));
                    i.code = code;
                    i.symbol = symbol(code);
                    i.label = label(code);
                    i.stamp = System.currentTimeMillis();

                    if (daily != null) {
                        org.json.JSONArray mx = daily.optJSONArray("temperature_2m_max");
                        org.json.JSONArray mn = daily.optJSONArray("temperature_2m_min");
                        if (mx != null && mx.length() > 0) i.max = (int) Math.round(mx.getDouble(0));
                        if (mn != null && mn.length() > 0) i.min = (int) Math.round(mn.getDouble(0));
                    }

                    p.edit()
                            .putInt("temp", i.temp)
                            .putInt("feels", i.feels)
                            .putInt("min", i.min)
                            .putInt("max", i.max)
                            .putInt("humidity", i.humidity)
                            .putInt("wind", i.wind)
                            .putInt("code", i.code)
                            .putString("symbol", i.symbol)
                            .putString("label", i.label)
                            .putLong("ts", i.stamp)
                            .apply();
                    cachedInfo = i;
                } catch (Throwable ignored) {
                } finally {
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    /** WMO-Wettercode -> Symbol. */
    static String symbol(int c) {
        if (c == 0) return "☀";
        if (c == 1) return "🌤";
        if (c == 2) return "⛅";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 51 && c <= 57) return "🌦";
        if (c >= 61 && c <= 67) return "🌧";
        if (c >= 71 && c <= 77) return "🌨";
        if (c >= 80 && c <= 82) return "🌧";
        if (c >= 85 && c <= 86) return "🌨";
        if (c >= 95) return "⛈";
        return "·";
    }

    /** WMO-Wettercode -> deutsches Wort. */
    static String label(int c) {
        if (c == 0) return "klar";
        if (c == 1) return "heiter";
        if (c == 2) return "wolkig";
        if (c == 3) return "bedeckt";
        if (c == 45) return "Nebel";
        if (c == 48) return "Reifnebel";
        if (c >= 51 && c <= 53) return "Sprühregen";
        if (c == 55) return "Nieselregen";
        if (c >= 56 && c <= 57) return "gefr. Niesel";
        if (c >= 61 && c <= 63) return "Regen";
        if (c == 65) return "Starkregen";
        if (c >= 66 && c <= 67) return "gefr. Regen";
        if (c >= 71 && c <= 73) return "Schneefall";
        if (c == 75) return "Starkschnee";
        if (c == 77) return "Schneegriesel";
        if (c >= 80 && c <= 81) return "Schauer";
        if (c == 82) return "Starkschauer";
        if (c >= 85 && c <= 86) return "Schneeschauer";
        if (c == 95) return "Gewitter";
        if (c >= 96) return "Gewitter+Hagel";
        return "";
    }
}
