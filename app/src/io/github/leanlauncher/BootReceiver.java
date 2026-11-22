package io.github.leanlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Sorgt dafuer, dass der Home-Service nach einem Neustart wieder laeuft.
 *
 * Hintergrund: Android schaltet Accessibility-Services bei jedem App-Update und
 * teilweise bei Neustarts ab (Sicherheitsfeature). Ohne diesen Receiver landet
 * der Home-Button danach wieder im Amazon-Launcher.
 *
 * Der Receiver traegt sich beim Booten wieder in enabled_accessibility_services ein -
 * das darf eine normale App nur, wenn sie bereits in der Liste stand (dann hat sie
 * das Recht, sich selbst wieder einzutragen). Ist das nicht der Fall, bleibt der
 * manuelle ADB-Befehl noetig.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String SVC = "io.github.leanlauncher/io.github.leanlauncher.HomeService";

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null) return;
        String action = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        if (Build.VERSION.SDK_INT < 26) return;

        try {
            String cur = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";

            if (!cur.contains(SVC)) {
                // Nur ergaenzen, wenn der Service schon einmal eingetragen war
                // (sonst verweigert Android das Schreiben).
                String next = cur.isEmpty() ? SVC : cur + ":" + SVC;
                Settings.Secure.putString(c.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
                Settings.Secure.putInt(c.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            } else {
                // Eintrag vorhanden, aber Flag aus -> wieder einschalten
                int en = Settings.Secure.getInt(c.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, 0);
                if (en != 1) {
                    Settings.Secure.putInt(c.getContentResolver(),
                            Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                }
            }
        } catch (SecurityException ignored) {
            // Ohne WRITE_SECURE_SETTINGS nicht erlaubt - dann bleibt der ADB-Weg.
        } catch (Exception ignored) { }
    }
}
