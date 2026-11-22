# Debloat — was sich abschalten lässt

Alles hier ist **reversibel**: `pm disable-user` lässt sich mit `pm enable`
rückgängig machen. Es wird nichts gelöscht, nichts geflasht, kein Root benutzt.

Getestet auf **Fire TV Stick 4K Plus (`AFTMA08C15`), Fire OS 8.1.8.5
(RS8185/3898), Android 11 / SDK 30**.

## Vorher sichern

```bash
# Alle Pakete und ihren Status wegsichern
adb shell pm list packages > pakete-vorher.txt
adb shell pm list packages -d > deaktiviert-vorher.txt
```

Rückweg für alles auf einmal:

```bash
for p in $(adb shell pm list packages -d | sed 's/package://'); do
  adb shell pm enable "$p"
done
```

---

## Gruppe 1 — Werbung

```bash
adb shell pm disable-user --user 0 com.amazon.hybridadidservice
adb shell pm disable-user --user 0 com.amazon.media.recommendations
adb shell pm disable-user --user 0 com.amazon.shoptv.client
adb shell pm disable-user --user 0 com.amazon.shoptv.firetv.client
adb shell pm disable-user --user 0 com.amazon.minitv.android.app
adb shell pm disable-user --user 0 com.amazon.sneakpeek
adb shell pm disable-user --user 0 com.amazon.tv.earlyaccess
```

**Nicht abschaltbar:** `com.amazon.ftvads.deeplinking` —
`SecurityException: Cannot disable a protected package`.

## Gruppe 2 — Telemetrie und Metriken

```bash
adb shell pm disable-user --user 0 com.amazon.device.telemetry.emitter
adb shell pm disable-user --user 0 com.amazon.perfcollection
adb shell pm disable-user --user 0 com.amazon.perfc
adb shell pm disable-user --user 0 com.amazon.wirelessmetrics.service
adb shell pm disable-user --user 0 com.amazon.ace.sensorsd.service
adb shell pm disable-user --user 0 com.amazon.aria
```

**Nicht abschaltbar:** `com.amazon.device.metrics`

## Gruppe 3 — Bildschirm-Auswertung (ACR)

```bash
adb shell pm disable-user --user 0 com.amazon.tv.acr
```

`com.amazon.tv.acr` wertet aus, **was auf dem Bildschirm läuft** — Bildanalyse zur
Erkennung von Inhalten, unabhängig davon, welche App läuft. Reines Tracking.
Möglicher Nebeneffekt: Amazons „X-Ray"-Funktionen können betroffen sein.

---

## Diese Pakete NICHT abschalten

| Paket | Warum nicht |
|---|---|
| `com.amazon.tv.launcher` | protected, nicht abschaltbar — und die Einstellungen laufen darin |
| `com.amazon.firehomestarter` | protected, fängt den Home-Button ab |
| `com.amazon.firebat` | **Prime Video** — ohne das kein Prime |
| `com.amazon.venezia` | Amazon Appstore — Installationsweg für Apps |
| `com.amazon.webview.chromium` | WebView; SmartTube und viele Apps brauchen das |
| `com.amazon.device.software.ota` | Updates. Abschalten heißt: keine Sicherheitsupdates mehr |
| `com.amazon.whisperlink.*` | Bildschirmspiegelung / Casting |
| `com.amazon.tv.settings.v2` | die Einstellungen selbst |
| `com.android.systemui` | Statusleiste, Tastatur, Systemdialoge |
| alles mit `whisperjoin`, `connection_manager`, `wifi`, `bluetooth` | Netzwerk und Kopplung |
| `fireos.hardware.*` | Audio, Video, Hardware |

**Faustregel:** Wenn der Paketname nach `settings`, `webview`, `wifi`, `bluetooth`,
`audio`, `ota`, `launcher` oder `firebat` klingt — Finger weg.

---

## Was das bringt (gemessen)

| | vorher | nachher |
|---|---|---|
| Deaktivierte Pakete | 0 | 14 |
| Werbebanner auf dem Homescreen | wechselnd (Spider-Man, Bosch, The Voice) | keine, weil eigener Launcher |
| ACR-Bildschirmauswertung | aktiv | aus |

Der RAM-Gewinn kommt **nicht** vom Debloat, sondern vom Launcher-Wechsel:
Amazons Launcher belegt ~130–170 MB, der eigene ~70 MB.

---

## Wenn etwas kaputtgeht

Einzeln wieder an:

```bash
adb shell pm enable com.amazon.<paketname>
```

Alles wieder an:

```bash
for p in $(adb shell pm list packages -d | sed 's/package://'); do
  adb shell pm enable "$p"
done
adb reboot
```

Nach einem Neustart ist der Ausgangszustand wiederhergestellt. Es gibt keinen
Zustand, aus dem man ohne Root nicht herauskommt — weil nichts am System selbst
verändert wird.
