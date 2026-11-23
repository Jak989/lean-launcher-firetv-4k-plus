package io.github.leanlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * Standort fuer das Wetter - automatisch oder selbst gesetzt.
 *
 * WARUM SO: Die App laeuft auf Fire OS, dort gibt es keine Google Play
 * Services und damit kein FusedLocationProvider fuer Drittanbieter. Es bleibt
 * der System-LocationManager (grob ueber WLAN) - der ist ungenau, reicht fuer
 * "welche Stadt" aber aus. Wenn keine Ortung erlaubt ist oder nichts
 * zurueckkommt, faellt alles auf die gespeicherte Stadt zurueck.
 *
 * Reihenfolge:
 *   1. Manuell gesetzte Stadt (hat immer Vorrang - der Nutzer entscheidet)
 *   2. Automatische Ortung, einmal beim ersten Start ermittelt
 *   3. Standard Muenchen
 *
 * Ort wird einmal ermittelt und dann gespeichert - kein Dauer-GPS, kein
 * Akkuverbrauch, keine Standortabfrage bei jedem Start.
 */
final class Place {

    private Place() { }

    private static final String PREF = "place";
    private static final int CACHE_VER = 1;

    /** Standard, wenn nichts gesetzt und keine Ortung moeglich ist. */
    static final double DEF_LAT = 48.090;
    static final double DEF_LON = 11.528;
    static final String DEF_CITY = "München";

    /** Bekannte Staedte zur Schnellauswahl - deckt die haeufigsten Faelle ab. */
    static final String[][] CITIES = {
            {"München", "48.137", "11.575"},
            {"Berlin", "52.520", "13.405"},
            {"Hamburg", "53.551", "9.993"},
            {"Köln", "50.938", "6.960"},
            {"Frankfurt", "50.110", "8.682"},
            {"Stuttgart", "48.775", "9.183"},
            {"Düsseldorf", "51.228", "6.773"},
            {"Leipzig", "51.340", "12.375"},
            {"Dresden", "51.050", "13.738"},
            {"Hannover", "52.375", "9.732"},
            {"Nürnberg", "49.454", "11.077"},
            {"Wien", "48.208", "16.373"},
            {"Zürich", "47.377", "8.541"},
            {"Salzburg", "47.809", "13.055"},
    };

    static final class Info {
        double lat = DEF_LAT;
        double lon = DEF_LON;
        String city = DEF_CITY;
        boolean manual = false;     // vom Nutzer gesetzt
        boolean located = false;    // per Ortung ermittelt
    }

    static Info load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Info i = new Info();
        if (p.getInt("ver", 0) != CACHE_VER) return i;
        i.lat = Double.longBitsToDouble(p.getLong("lat", Double.doubleToLongBits(DEF_LAT)));
        i.lon = Double.longBitsToDouble(p.getLong("lon", Double.doubleToLongBits(DEF_LON)));
        i.city = p.getString("city", DEF_CITY);
        i.manual = p.getBoolean("manual", false);
        i.located = p.getBoolean("located", false);
        return i;
    }

    static void save(Context c, Info i) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("lat", Double.doubleToLongBits(i.lat))
                .putLong("lon", Double.doubleToLongBits(i.lon))
                .putString("city", i.city)
                .putBoolean("manual", i.manual)
                .putBoolean("located", i.located)
                .putInt("ver", CACHE_VER)
                .apply();
    }

    /** Stadt manuell setzen. */
    static void setCity(Context c, String city, double lat, double lon) {
        Info i = new Info();
        i.city = city;
        i.lat = lat;
        i.lon = lon;
        i.manual = true;
        save(c, i);
    }

    /** Zurueck auf automatisch (Ortung beim naechsten Start). */
    static void setAuto(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean("manual", false)
                .putBoolean("located", false)
                .putInt("ver", CACHE_VER)
                .apply();
    }

    /**
     * Ermittelt den Ort per System-Ortung und speichert ihn.
     * Ruft onDone immer auf - auch bei Fehlschlag oder fehlender Erlaubnis.
     */
    static void locateIfNeeded(final Context c, final Runnable onDone) {
        final Info cur = load(c);
        if (cur.manual || cur.located) {
            if (onDone != null) onDone.run();
            return;
        }

        final LocationManager lm =
                (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            if (onDone != null) onDone.run();
            return;
        }

        boolean allowed = false;
        try {
            allowed = c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException ignored) { }
        if (!allowed) {
            android.util.Log.i("LEANLOC", "Keine Ortungs-Erlaubnis - bleibt bei " + cur.city);
            if (onDone != null) onDone.run();
            return;
        }

        final Handler ui = new Handler(Looper.getMainLooper());
        final boolean[] fertig = {false};

        final Runnable finish = new Runnable() {
            public void run() {
                if (fertig[0]) return;
                fertig[0] = true;
                if (onDone != null) onDone.run();
            }
        };

        // Letzten bekannten Stand probieren - sofort verfuegbar, kein Warten
        try {
            Location last = null;
            for (String prov : new String[]{LocationManager.NETWORK_PROVIDER,
                    LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(prov);
                    if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                } catch (RuntimeException ignored) { }
            }
            if (last != null) {
                applyAndSave(c, last.getLatitude(), last.getLongitude(), finish);
                return;
            }
        } catch (RuntimeException ignored) { }

        // Sonst kurz aktiv anfragen (max. 8 s)
        try {
            final LocationListener listener = new LocationListener() {
                public void onLocationChanged(Location l) {
                    try { lm.removeUpdates(this); } catch (RuntimeException ignored) { }
                    if (l != null) applyAndSave(c, l.getLatitude(), l.getLongitude(), finish);
                    else finish.run();
                }
                public void onStatusChanged(String p, int s, Bundle b) { }
                public void onProviderEnabled(String p) { }
                public void onProviderDisabled(String p) { }
            };

            List<String> provs = lm.getProviders(true);
            boolean any = false;
            for (String p : provs) {
                try {
                    lm.requestLocationUpdates(p, 0, 0, listener);
                    any = true;
                } catch (RuntimeException ignored) { }
            }
            if (!any) { finish.run(); return; }

            // Zeitlimit: danach aufgeben und beim Standard bleiben
            ui.postDelayed(new Runnable() {
                public void run() {
                    try { lm.removeUpdates(listener); } catch (RuntimeException ignored) { }
                    finish.run();
                }
            }, 8000);
        } catch (RuntimeException e) {
            finish.run();
        }
    }

    /** Koordinaten in einen Stadtnamen uebersetzen und speichern. */
    private static void applyAndSave(final Context c, final double lat, final double lon,
                                     final Runnable onDone) {
        new Thread(new Runnable() {
            public void run() {
                String city = cityFor(lat, lon);
                Info i = new Info();
                i.lat = lat;
                i.lon = lon;
                i.city = city;
                i.manual = false;
                i.located = true;
                save(c, i);
                android.util.Log.i("LEANLOC", "Ort ermittelt: " + city
                        + " (" + lat + ", " + lon + ")");
                if (onDone != null) onDone.run();
            }
        }).start();
    }

    /**
     * Stadtname zu Koordinaten. Erst der System-Geocoder (offline-faehig),
     * sonst Open-Meteo - die Wetter-API liefert den Ortsnamen gratis mit.
     */
    private static String cityFor(double lat, double lon) {
        // 1. System-Geocoder (falls vorhanden - auf Fire OS oft nicht)
        try {
            Context c = LastContext.get();
            if (c != null) {
                Geocoder g = new Geocoder(c, Locale.GERMANY);
                List<Address> a = g.getFromLocation(lat, lon, 1);
                if (a != null && !a.isEmpty()) {
                    Address ad = a.get(0);
                    String city = ad.getLocality();
                    if (city == null || city.length() == 0) city = ad.getSubAdminArea();
                    if (city == null || city.length() == 0) city = ad.getAdminArea();
                    if (city != null && city.length() > 0) return city;
                }
            }
        } catch (Throwable ignored) { }

        // 2. Open-Meteo kennt kein Reverse-Geocoding. Stattdessen: die
        // naechstgelegene bekannte Stadt aus der eigenen Liste nehmen.
        // Fuer "welche Stadt" ist das genau genug und braucht kein Netz.
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (String[] city : CITIES) {
            double clat = Double.parseDouble(city[1]);
            double clon = Double.parseDouble(city[2]);
            double d = Math.abs(clat - lat) + Math.abs(clon - lon);
            if (d < bestDist) { bestDist = d; best = city[0]; }
        }
        // Nur uebernehmen, wenn es halbwegs passt (ca. 0.6 Grad ~ 60 km)
        if (best != null && bestDist < 0.6) return best;

        return "Mein Ort";
    }

    /**
     * Kleiner Halter fuer den ApplicationContext - der Geocoder braucht einen,
     * die Hilfsmethode laeuft aber im Hintergrund-Thread ohne Context-Parameter.
     */
    static final class LastContext {
        private static volatile Context ctx;
        static void set(Context c) { ctx = c.getApplicationContext(); }
        static Context get() { return ctx; }
    }

    /** "48.09, 11.53" -> Anzeige mit einer Nachkommastelle. */
    static String coordText(Info i) {
        return String.format(Locale.GERMANY, "%.2f, %.2f", i.lat, i.lon);
    }
}
