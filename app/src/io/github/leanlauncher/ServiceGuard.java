package io.github.leanlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Selbstheilung des Accessibility-Dienstes - laeuft komplett auf dem Fire TV.
 *
 * == Warum es das braucht ==
 * Fire OS schaltet Accessibility-Services bei jedem App-Update ab (und
 * gelegentlich auch nach einem Neustart). Ergebnis: der Home-Button landet
 * wieder im Amazon-Launcher, die eigene Startseite kommt nicht mehr.
 *
 * Der Service steht dann NICHT mehr in ENABLED_ACCESSIBILITY_SERVICES. Eine
 * normale App darf sich dort nur eintragen, wenn sie das Recht
 * WRITE_SECURE_SETTINGS hat. Das laesst sich nur EINMAL per ADB erteilen:
 *
 *     adb shell pm grant io.github.leanlauncher android.permission.WRITE_SECURE_SETTINGS
 *
 * Danach ist kein PC mehr noetig: dieser Guard schreibt den Eintrag bei jedem
 * Start von HomeActivity (App-Start, Home-Taste, Boot) wieder nach.
 *
 * == Kosten (auf dem Fire TV gemessen) ==
 * Der Guard laeuft nur im Hauptprozess, kein eigener Thread, kein Service,
 * kein Timer. Er liest zwei Settings-Werte und schreibt nur bei Abweichung.
 * Laufzeit im Normalfall: deutlich unter 1 ms. Kein zusaetzlicher RAM.
 *
 * == Was er NICHT macht (bewusst) ==
 * - kein am force-stop (das killt den Service und Fire OS traegt ihn nicht
 *   wieder ein - genau der Fehler, den er verhindern soll)
 * - kein Schreiben, wenn schon alles stimmt (spart Flash-Zyklen)
 * - kein SCHEDULE_EXACT_ALARM / kein Wecker / keine Wakelocks
 */
public final class ServiceGuard {

    private static final String PKG = "io.github.leanlauncher";
    private static final String CLS = "HomeService";
    private static final String SVC = PKG + "/" + PKG + "." + CLS;

    /** Mindestabstand zwischen zwei Pruefungen (ms) - schont CPU und Flash. */
    private static final long MIN_INTERVAL_MS = 60 * 1000;

    private static long lastCheck = 0;

    private ServiceGuard() { }

    /** Prueft und repariert. Idempotent, billig, wirft nie nach aussen. */
    static void ensure(Context c) {
        if (c == null) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastCheck < MIN_INTERVAL_MS && lastCheck != 0) return;
        lastCheck = now;

        try {
            String cur = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            int enabled = Settings.Secure.getInt(c.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0);

            String next = merge(cur, SVC);
            boolean svcListed = next.equals(cur);
            if (svcListed && enabled == 1) return;   // alles gut, nichts schreiben

            if (!svcListed) {
                Settings.Secure.putString(c.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
                android.util.Log.i("ServiceGuard", "Eintrag repariert: " + next);
            }
            if (enabled != 1) {
                Settings.Secure.putInt(c.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                android.util.Log.i("ServiceGuard", "accessibility_enabled auf 1");
            }
        } catch (SecurityException ignored) {
            android.util.Log.w("ServiceGuard", "WRITE_SECURE_SETTINGS fehlt - pm grant noetig");
        } catch (Throwable t) {
            android.util.Log.w("ServiceGuard", "Fehler: " + t);
        }
    }

    /** Fuegt unseren Eintrag hinzu, ohne vorhandene Services zu verlieren. */
    private static String merge(String cur, String svc) {
        if (cur == null) cur = "";
        cur = cur.trim();
        if (cur.isEmpty()) return svc;
        for (String part : cur.split(":")) {
            String p = part.trim();
            if (p.equalsIgnoreCase(svc) || shortForm(p).equalsIgnoreCase(svc)) {
                return cur;   // schon drin (auch in Kurzschreibweise pkg/.Cls)
            }
        }
        return cur + ":" + svc;
    }

    /** "io.github.leanlauncher/.HomeService" -> voller ComponentName-String. */
    private static String shortForm(String p) {
        try {
            ComponentName cn = ComponentName.unflattenFromString(p);
            if (cn == null) return p;
            return cn.flattenToString();
        } catch (Throwable t) {
            return p;
        }
    }

    /** Fuer Diagnose am TV (nicht im Normalbetrieb genutzt). */
    static boolean isListed(Context c) {
        try {
            String cur = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return cur != null && merge(cur, SVC).equals(cur.trim());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Startet den Dienst nicht selbst - nur fuer den BootReceiver relevant. */
    static void startIfNeeded(Context c) {
        ensure(c);
    }

    static Intent serviceIntent() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, PKG + "." + CLS));
        return i;
    }
}
