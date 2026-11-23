package io.github.leanlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Sorgt dafuer, dass der Home-Service nach einem Neustart oder App-Update
 * wieder eingetragen ist.
 *
 * Die eigentliche Arbeit macht ServiceGuard.ensure(): es liest den aktuellen
 * Eintrag und schreibt nur, wenn unser Service fehlt oder das A11y-Flag aus
 * ist. Mit dem Recht WRITE_SECURE_SETTINGS (einmalig per pm grant erteilt)
 * laeuft das komplett ohne PC.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || c == null) return;
        String action = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        // Direkt nach dem Boot ist das Settings-System manchmal noch nicht da:
        // dann beim naechsten Start von HomeActivity (0,5 s) noch einmal pruefen.
        ServiceGuard.ensure(c);
        if (Build.VERSION.SDK_INT >= 26) {
            // Der Guard schreibt bei Bedarf nach; kein eigener Thread noetig.
            ServiceGuard.startIfNeeded(c);
        }
    }
}
