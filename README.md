# Lean Launcher

Ein schlanker, werbefreier Startbildschirm für den **Amazon Fire TV Stick 4K Plus**
(Fire OS 8, getestet auf `AFTMA08C15`) — gebaut ohne Root, ohne Bootloader-Unlock,
ohne alternative Firmware.

> **Es ist dein Fernseher. Du entscheidest, was darauf läuft.**
>
> Der Fire TV zeigt beim Einschalten Werbung, Empfehlungen und wechselnde
> Kampagnen-Kacheln. Wer nur seine Sender und seine Apps sehen will, muss jedes Mal
> daran vorbeiklicken. Das Gerät gehört aber dem Käufer, nicht dem Hersteller —
> und mit ein paar `adb`-Befehlen kommt man an die Stelle, an der diese Oberfläche
> gesteuert wird. Genau das macht dieses Projekt. Jeder kann das an seinem eigenen
> Gerät machen.

---

## Warum das existiert

Drei Wünsche, sonst nichts:

1. **Meine Sender sofort.** Der Fernseher geht an, ich drücke eine Taste, ich sehe
   das Programmheft mit laufender Sendung, Restzeit und Vorschau. Kein Suchen.
2. **Meine Apps sofort.** Alles, was ich nutze, auf einem Blick — ohne Scrollen,
   ohne eine Zeile, die nach rechts wegläuft.
3. **Keine Werbung für irgendeinen Scheiß.** Kein Werbebanner, keine
   Amazon-Empfehlungen, keine Sendungskacheln, die mich zum Kaufen bringen sollen.

Das ist die ganze Intention. Es ist kein Produkt, es ist eine Wohnzimmer-Oberfläche
für Leute, die ihren Fernseher einfach nur benutzen wollen.

---

## Was es kann

| Funktion | Beschreibung |
|---|---|
| **Werbefreier Start** | Amazons Homescreen bleibt verdeckt, sobald der Fire TV startet oder Home gedrückt wird |
| **Live-TV** | 11 öffentlich-rechtliche Sender mit laufender Sendung, Fortschrittsbalken und laufender Vorschau im Vorschaifenster |
| **Nachrichten** | Tagesschau, Tagesthemen und Deutschlandfunk auf Abruf (kein Live-Radio) |
| **App-Raster** | Installierte Apps auf einer Seite, Favoriten zuerst, Amazon-Apps ausgeblendet |
| **Uhr, Datum, Wetter** | Oben im Kopf; Wetter über Open-Meteo, ohne API-Key |
| **Art Mode nach 3 min** | Ohne Tastendruck werden Kunstbilder gezeigt, Bildschirm auf 15 % gedimmt |
| **Debloat-Anleitung** | Welche Amazon-Pakete sich abschalten lassen — und welche man in Ruhe lassen sollte |

---

## Wie die Sender und Podcasts laufen

Alle Quellen sind **DRM-frei** und wurden einzeln gegen HTTP 200 plus gültige
Playlist bzw. `audio/mpeg` geprüft. Wer eine URL ändert, sollte sie erneut prüfen.

**Live-TV (HLS):**

| Sender | Quelle |
|---|---|
| Das Erste, KiKA, ONE, ARD alpha | `*.ard-mcdn.de` (ARD) |
| ZDF, 3sat, ZDFinfo, ZDFneo, phoenix | `*.akamaized.net` (ZDF) |
| arte | `artesimulcast.akamaized.net` |
| tagesschau24 | `tagesschau.akamaized.net` |

**EPG:** `epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz` (XMLTV).
Die Datei ist entpackt ~25 MB — sie wird **zeilenweise gestreamt** und nur die
11 gewünschten Sender werden behalten und lokal zwischengespeichert.

**Nachrichten:** ARD-Audiothek GraphQL (`api.ardaudiothek.de`) liefert die neuesten
Folgen mit direkter MP3-URL.

**Wetter:** Open-Meteo (`api.open-meteo.com`), kostenlos, kein Schlüssel.

> Hinweis zur Rechtelage: Die Streams werden nur abgespielt, nicht gespeichert oder
> weiterverbreitet. Das entspricht dem, was die ARD-Mediathek im Browser auch tut.
> Für die Sendungsdaten gilt die Lizenz der jeweiligen Quelle.

---

## Wie der Home-Button überlistet wird

**Das ist der technisch interessante Teil.**

Fire OS erlaubt keinen zweiten Standard-Homescreen:

```
$ adb shell pm disable-user --user 0 com.amazon.tv.launcher
java.lang.SecurityException: Cannot disable a protected package: com.amazon.tv.launcher
```

Dasselbe bei `com.amazon.firehomestarter`, das den Home-Tasten-Druck abfängt.
Auch `pm set-home-activity`, `settings put secure preferred_home_activity` und
`android:priority` im `HOME`-Intent-Filter greifen nicht — Firefox OS ignoriert sie
schlicht.

Der Weg, der funktioniert: ein **Accessibility-Service** darf ein Overlay über alles
legen. Sobald Amazons `HomeActivity` nach vorne kommt, wird das Overlay sichtbar und
im gleichen Moment die eigene Startseite gestartet. Sobald die gezeichnet hat,
verschwindet das Overlay — man sieht nur die eigene Oberfläche.

```
Amazon HomeActivity erscheint
        ↓
Overlay (schwarz) wird sichtbar
        ↓
eigene HomeActivity startet
        ↓
HomeActivity meldet "ich bin da"
        ↓
Overlay verschwindet
```

Was dabei zu beachten war:

- **Die Fire-TV-Einstellungen laufen IM Amazon-Launcher-Paket.** Ohne
  Sonderbehandlung wird das Overlay über die Einstellungen gelegt, das System holt sie
  zurück, das Overlay kommt wieder — eine Endlosschleife. Der Service erkennt
  `*Settings*`-Klassen und pausiert sich für 10 Minuten.
- **Ein Cooldown von 3 s** verhindert Mehrfach-Auslösung bei mehreren Events.
- **`am force-stop` beendet den Accessibility-Service**, und Fire OS trägt ihn danach
  *nicht* wieder ein. Deshalb setzt `deploy.sh` den Service nach jedem Install neu und
  **prüft nach**, statt zu hoffen.

Diese Variante ist gut benutzbar, aber nicht unsichtbar: bei jedem App-Update schaltet
Android den Accessibility-Service ab und er muss neu gesetzt werden. Das ist
Fire-OS-Politik, nicht behebbar ohne Root.

---

## Bauen

Voraussetzungen: JDK (17+), Android SDK mit `platforms/android-35` und
`build-tools/` — kein Android Studio, kein Gradle nötig.

```bash
./build.sh
```

Ablauf: `aapt2 → javac → d8 → zipalign → apksigner`.
Beim ersten Lauf wird ein Keystore erzeugt; das Passwort kommt aus `$KS_PASS`
(Standard: `localdev`).

## Installieren

ADB-Debugging am Stick aktivieren:
**Einstellungen → My Fire TV → Info** → 7× auf den Gerätenamen →
**Entwickleroptionen → ADB-Debugging an**.

```bash
./deploy.sh <firetv-ip>:5555
```

`deploy.sh` baut, installiert, gibt die nötigen Rechte, aktiviert den
Accessibility-Service und prüft am Ende, ob er wirklich gebunden ist.

Ohne Argumente wird `$FIRE_TV_IP` verwendet.

## Wieder rückgängig machen

```bash
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb shell pm uninstall io.github.leanlauncher
```

Danach ist der Home-Button wieder bei Amazon. Es wird nichts am System verändert —
alle Änderungen sind Einstellungen, die sich zurücksetzen lassen.

---

## Aufbau des Codes

```
app/src/io/github/leanlauncher/
├── HomeActivity.java        Startbildschirm (Kopf, Kacheln, App-Raster)
├── HomeService.java         Accessibility-Service, hält Amazons Home verdeckt
├── ChannelListActivity.java Senderliste mit Vorschau und EPG
├── LiveActivity.java        Vollbild-Wiedergabe mit Zappen
├── NewsActivity.java        Nachrichten-Podcasts auf Abruf
├── Epg.java                 XMLTV-Streaming + lokaler Cache
├── Podcasts.java            ARD-Audiothek GraphQL
├── Weather.java             Open-Meteo mit Cache
├── Channels.java            Senderliste + EPG-IDs
├── Radio.java               Nachrichten-Radios (geprüft, derzeit ungenutzt)
├── Ui.java                  Farben und Masse
├── BootReceiver.java        Service nach Neustart wieder eintragen
└── HomeActivity (Apps)      App-Erkennung über PackageManager
```

**Performance-Regeln, die eingehalten werden:**

- Views werden **einmal** gebaut. Änderungen tauschen nur Text und Farbe.
- Kein Netzwerk im UI-Thread. EPG und Wetter kommen aus dem Cache.
- Genau **ein** `VideoView`, wird für jeden Sender wiederverwendet.

## Debloat (optional)

Der Launcher ersetzt die Oberfläche, aber Amazons Dienste laufen im Hintergrund weiter.
Was sich gefahrlos abschalten lässt — und was nicht — steht in
[`docs/debloat.md`](docs/debloat.md).

---

## Grenzen

- **Kein Root, keine Custom-Firmware.** Der Bootloader ist signiert, es gibt keinen
  öffentlichen Unlock für diese Generation. Das ist keine Einschränkung dieses
  Projekts, sondern eine Eigenschaft des Geräts.
- **Der Amazon-Launcher läuft weiter im Hintergrund** (~130 MB), weil er nicht
  deinstallierbar ist.
- **Nach jedem Update** muss `deploy.sh` erneut laufen.
- **ARD- und ZDF-URLs ändern sich.** Wenn ein Sender schwarz bleibt, ist meist die
  Stream-URL veraltet und muss neu gesucht werden.

## Lizenz

MIT — mach damit, was du willst.
