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
 * Wetter ueber Open-Meteo (kostenlos, kein API-Key, keine Registrierung).
 *
 * Liefert:
 *   - aktuelles Wetter mit Symbol, gefuehlter Temperatur, Min/Max
 *   - Luftfeuchte, Wind, Niederschlagsrisiko, Sonnenauf-/-untergang
 *   - 7-Tage-Vorhersage (Code, Max, Min, Regenwahrscheinlichkeit)
 *
 * Ergebnis wird 20 Minuten zwischengespeichert - kein Netzwerk beim Zeichnen.
 * Alles bleibt offline-fest: schlaegt das Netz fehl, wird der letzte Stand gezeigt.
 */
final class Weather {

    private Weather() { }

    /** Koordinaten kommen aus Place (Ortung oder manuell gesetzt). */
    static double LAT = Place.DEF_LAT;
    static double LON = Place.DEF_LON;
    static String CITY = Place.DEF_CITY;

    /** Vom Ort abhaengigen Zustand setzen. */
    static void usePlace(Place.Info p) {
        LAT = p.lat;
        LON = p.lon;
        CITY = p.city;
    }

    /** 8 Tage anfordern: heute + 7 Folgetage. Wird pro Abruf gebaut. */
    private static String buildUrl() {
        return "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + LAT + "&longitude=" + LON
            + "&current=temperature_2m,weather_code,relative_humidity_2m,"
            +   "apparent_temperature,wind_speed_10m,is_day"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
            +   "precipitation_probability_max,sunrise,sunset"
            // Stundenwerte fuer den Tagesverlauf - ohne die war die Kurve
            // nur aus Min/Max geformt und damit erfunden.
            + "&hourly=temperature_2m,precipitation,precipitation_probability,"
            +   "weather_code,wind_speed_10m,relative_humidity_2m"
            + "&timezone=auto&forecast_days=8";
    }

    private static final long CACHE_MS = 20 * 60 * 1000;
    /**
     * Cache-Format-Version. Wenn sich die gespeicherten Felder aendern, hier
     * hochzaehlen - sonst zeigt die App bis zu CACHE_MS lang den alten,
     * unvollstaendigen Stand (z.B. ohne 7-Tage-Daten).
     */
    private static final int CACHE_VER = 5;
    private static final String PREF = "weather";
    private static final String PREF_DAYS = "weather_days";

    /** Ein Vorhersagetag. */
    static final class Day {
        String date = "";   // 2026-11-22
        int code = -1;
        int max = 0;
        int min = 0;
        int rain = -1;      // % Regenwahrscheinlichkeit, -1 = unbekannt
        boolean today = false;

        boolean valid() { return code >= 0; }
    }

    /** Aktuelles Wetter. */
    static final class Info {
        int temp = 0;
        int feels = 0;
        int min = 0;
        int max = 0;
        int humidity = 0;
        int wind = 0;
        int rain = -1;
        int code = -1;
        boolean day = true;
        String symbol = "";
        String label = "";
        String sunrise = "";
        String sunset = "";
        long stamp = 0;
        Day[] days = new Day[0];
        /** Echte Stundenwerte fuer heute (24 Werte ab 0 Uhr, -999 = fehlt). */
        int[] hourTemp = new int[0];
        int[] hourRain = new int[0];
        int[] hourCode = new int[0];
        /** Regenmenge in mm*10 (0.1 mm Schritte, ganzzahlig gespeichert). */
        int[] hourMm = new int[0];
        int[] hourWind = new int[0];
        int[] hourHum = new int[0];

        boolean valid() { return code >= 0; }
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
        i.rain = p.getInt("rain", -1);
        i.code = p.getInt("code", -1);
        i.day = p.getBoolean("day", true);
        i.symbol = p.getString("symbol", "");
        i.label = p.getString("label", "");
        i.sunrise = p.getString("sunrise", "");
        i.sunset = p.getString("sunset", "");
        i.stamp = p.getLong("ts", 0);
        SharedPreferences pd = c.getSharedPreferences(PREF_DAYS, Context.MODE_PRIVATE);
        i.days = readDays(pd);
        if (pd.getInt("hver", 0) == CACHE_VER) {
            String hs = pd.getString("hours", "");
            if (hs != null && hs.length() > 0) {
                String[] lines = hs.split("\n");
                int n = Math.min(48, lines.length);
                i.hourTemp = new int[n];
                i.hourRain = new int[n];
                i.hourMm = new int[n];
                i.hourWind = new int[n];
                i.hourHum = new int[n];
                for (int k = 0; k < n; k++) {
                    String[] f = lines[k].split(",");
                    if (f.length >= 2) {
                        i.hourTemp[k] = parseInt(f[0], -999);
                        i.hourRain[k] = parseInt(f[1], -1);
                        if (f.length >= 3) i.hourMm[k] = parseInt(f[2], 0);
                        if (f.length >= 4) i.hourWind[k] = parseInt(f[3], 0);
                        if (f.length >= 5) i.hourHum[k] = parseInt(f[4], 0);
                    }
                }
            }
        }
        cachedInfo = i;
        return i;
    }

    /** Liest die gespeicherte Vorhersage. */
    static Day[] readDays(SharedPreferences p) {
        String raw = p.getString("raw", "");
        if (raw == null || raw.length() == 0) return new Day[0];
        String[] lines = raw.split("\n");
        java.util.ArrayList<Day> out = new java.util.ArrayList<Day>();
        for (String ln : lines) {
            String[] f = ln.split(";");
            if (f.length < 5) continue;
            Day d = new Day();
            d.date = f[0];
            d.code = parseInt(f[1], -1);
            d.max = parseInt(f[2], 0);
            d.min = parseInt(f[3], 0);
            d.rain = parseInt(f[4], -1);
            d.today = f.length > 5 && f[5].equals("1");
            if (d.valid()) out.add(d);
        }
        return out.toArray(new Day[0]);
    }

    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return dflt; }
    }

    /** Aktualisiert im Hintergrund, falls aelter als 20 Minuten. */
    static void refresh(final Context c, final Runnable onDone) {
        final SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("ts", 0) < CACHE_MS
                && p.getInt("ver", 0) == CACHE_VER) {
            android.util.Log.i("LEANW", "refresh: Cache frisch, kein Netz");
            if (onDone != null) onDone.run();
            return;
        }
        android.util.Log.i("LEANW", "refresh: lade " + buildUrl());

        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(buildUrl()).openConnection();
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
                    JSONObject daily = root.optJSONObject("daily");

                    int code = cur.getInt("weather_code");
                    boolean isDay = cur.optInt("is_day", 1) == 1;
                    Info i = new Info();
                    i.temp = (int) Math.round(cur.getDouble("temperature_2m"));
                    i.feels = (int) Math.round(cur.optDouble("apparent_temperature", 0));
                    i.humidity = cur.optInt("relative_humidity_2m", 0);
                    i.wind = (int) Math.round(cur.optDouble("wind_speed_10m", 0));
                    i.code = code;
                    i.day = isDay;
                    i.symbol = symbol(code, isDay);
                    i.label = label(code);
                    i.stamp = System.currentTimeMillis();

                    StringBuilder days = new StringBuilder();
                    int dayCount = 0;

                    if (daily != null) {
                        JSONArray dt = daily.optJSONArray("time");
                        JSONArray dcode = daily.optJSONArray("weather_code");
                        JSONArray mx = daily.optJSONArray("temperature_2m_max");
                        JSONArray mn = daily.optJSONArray("temperature_2m_min");
                        JSONArray rp = daily.optJSONArray("precipitation_probability_max");
                        JSONArray sr = daily.optJSONArray("sunrise");
                        JSONArray ss = daily.optJSONArray("sunset");

                        if (mx != null && mx.length() > 0) i.max = (int) Math.round(mx.getDouble(0));
                        if (mn != null && mn.length() > 0) i.min = (int) Math.round(mn.getDouble(0));
                        if (rp != null && rp.length() > 0) i.rain = rp.optInt(0, -1);
                        if (sr != null && sr.length() > 0) i.sunrise = hhmm(sr.optString(0, ""));
                        if (ss != null && ss.length() > 0) i.sunset = hhmm(ss.optString(0, ""));

                        if (dt != null && dcode != null && mx != null && mn != null) {
                            int n = Math.min(dt.length(), Math.min(dcode.length(),
                                    Math.min(mx.length(), mn.length())));
                            if (n > 8) n = 8;
                            Day[] arr = new Day[n];
                            for (int k = 0; k < n; k++) {
                                Day d = new Day();
                                d.date = dt.optString(k, "");
                                d.code = dcode.optInt(k, -1);
                                d.max = (int) Math.round(mx.optDouble(k, 0));
                                d.min = (int) Math.round(mn.optDouble(k, 0));
                                d.rain = rp != null ? rp.optInt(k, -1) : -1;
                                d.today = (k == 0);
                                arr[k] = d;
                                days.append(d.date).append(';').append(d.code).append(';')
                                    .append(d.max).append(';').append(d.min).append(';')
                                    .append(d.rain).append(';').append(d.today ? 1 : 0)
                                    .append('\n');
                                dayCount++;
                            }
                            i.days = arr;
                        }
                    }

                    // ---- Stundenwerte fuer den Tagesverlauf ----
                    JSONObject hourly = root.optJSONObject("hourly");
                    if (hourly != null) {
                        JSONArray ht = hourly.optJSONArray("temperature_2m");
                        JSONArray hr = hourly.optJSONArray("precipitation_probability");
                        JSONArray hc = hourly.optJSONArray("weather_code");
                        JSONArray hmm = hourly.optJSONArray("precipitation");
                        JSONArray hw = hourly.optJSONArray("wind_speed_10m");
                        JSONArray hh = hourly.optJSONArray("relative_humidity_2m");
                        if (ht != null && ht.length() >= 24) {
                            int N = Math.min(48, ht.length());
                            i.hourTemp = new int[N];
                            i.hourRain = new int[N];
                            i.hourCode = new int[N];
                            i.hourMm = new int[N];
                            i.hourWind = new int[N];
                            i.hourHum = new int[N];
                            for (int k = 0; k < N; k++) {
                                i.hourTemp[k] = (int) Math.round(ht.optDouble(k, -999));
                                i.hourRain[k] = hr != null ? hr.optInt(k, -1) : -1;
                                i.hourCode[k] = hc != null ? hc.optInt(k, -1) : -1;
                                // mm in Zehntel, damit ganzzahlig gespeichert
                                i.hourMm[k] = hmm != null
                                        ? (int) Math.round(hmm.optDouble(k, 0) * 10) : 0;
                                i.hourWind[k] = hw != null
                                        ? (int) Math.round(hw.optDouble(k, 0)) : 0;
                                i.hourHum[k] = hh != null ? hh.optInt(k, 0) : 0;
                            }
                        }
                    }

                    p.edit()
                            .putInt("temp", i.temp)
                            .putInt("feels", i.feels)
                            .putInt("min", i.min)
                            .putInt("max", i.max)
                            .putInt("humidity", i.humidity)
                            .putInt("wind", i.wind)
                            .putInt("rain", i.rain)
                            .putInt("code", i.code)
                            .putBoolean("day", i.day)
                            .putString("symbol", i.symbol)
                            .putString("label", i.label)
                            .putString("sunrise", i.sunrise)
                            .putString("sunset", i.sunset)
                            .putLong("ts", i.stamp)
                            .putInt("ver", CACHE_VER)
                            .apply();

                    if (dayCount > 0) {
                        c.getSharedPreferences(PREF_DAYS, Context.MODE_PRIVATE).edit()
                                .putString("raw", days.toString()).apply();
                    }
                    if (i.hourTemp.length >= 24) {
                        StringBuilder hs = new StringBuilder();
                        for (int k = 0; k < i.hourTemp.length; k++) {
                            hs.append(i.hourTemp[k]).append(',')
                              .append(i.hourRain[k]).append(',')
                              .append(i.hourMm[k]).append(',')
                              .append(i.hourWind[k]).append(',')
                              .append(i.hourHum[k]).append('\n');
                        }
                        c.getSharedPreferences(PREF_DAYS, Context.MODE_PRIVATE).edit()
                                .putString("hours", hs.toString())
                                .putInt("hver", CACHE_VER)
                                .apply();
                    }
                    android.util.Log.i("LEANW", "Wetter ok: " + i.temp + "° " + i.label
                            + " / " + dayCount + " Tage");
                    cachedInfo = i;
                } catch (Throwable t) {
                    android.util.Log.w("LEANW", "Wetter fehlgeschlagen: " + t);
                } finally {
                    if (onDone != null) onDone.run();
                }
            }
        }).start();
    }

    /** "2026-11-22T07:30" -> "07:30" */
    private static String hhmm(String iso) {
        int t = iso.indexOf('T');
        if (t < 0 || iso.length() < t + 6) return "";
        return iso.substring(t + 1, t + 6);
    }

    /** WMO-Wettercode -> Zeichen. Tag und Nacht getrennt, damit nachts der Mond steht. */
    static String symbol(int c, boolean day) {
        if (c == 0) return day ? "☀" : "🌙";
        if (c == 1) return day ? "🌤" : "🌙";
        if (c == 2) return day ? "⛅" : "☁";
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

    /** Kompatibilitaet: Tag-Symbol. */
    static String symbol(int c) { return symbol(c, true); }

    /** WMO-Wettercode -> deutsches Wort (kurz, fuer die 7-Tage-Spalten). */
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

    /** Ganz kurzes Wort fuer schmale Spalten: "Schneefall" -> "Schnee". */
    static String shortLabel(int c) {
        if (c == 0) return "klar";
        if (c == 1) return "heiter";
        if (c == 2) return "wolkig";
        if (c == 3) return "bedeckt";
        if (c == 45 || c == 48) return "Nebel";
        if (c >= 51 && c <= 57) return "Niesel";
        if (c >= 61 && c <= 67) return "Regen";
        if (c >= 71 && c <= 77) return "Schnee";
        if (c >= 80 && c <= 82) return "Schauer";
        if (c >= 85 && c <= 86) return "Schnee";
        if (c >= 95) return "Gewitter";
        return "";
    }
}
