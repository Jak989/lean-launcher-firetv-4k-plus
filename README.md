# Lean Launcher — Fire TV Stick 4K Plus ohne Werbung

**Werbefreier Startbildschirm als Alternative zum Amazon-Launcher** für den
**Amazon Fire TV Stick 4K Plus** (Fire OS 8 / Android 11, getestet auf `AFTMA08C15`).

Statt Amazons Homescreen mit Werbebannern, Film- und Serienempfehlungen und
wechselnden Kampagnen-Kacheln zeigt dieser Launcher nur das, was man wirklich
braucht: **Live-TV, Nachrichten, die eigenen Apps, Uhr, Datum und Wetter.**

Gebaut **ohne Root, ohne Bootloader-Unlock, ohne Custom-Firmware** — nur mit `adb`.
Jede Änderung ist umkehrbar.

> **Es ist dein Fernseher. Du entscheidest, was darauf läuft.**
>
> Der Fire TV zeigt beim Einschalten Werbung, Empfehlungen und wechselnde
> Kampagnen-Kacheln. Wer nur seine Sender und seine Apps sehen will, muss jedes Mal
> daran vorbeiklicken. Das Gerät gehört aber dem Käufer, nicht dem Hersteller —
> und mit ein paar `adb`-Befehlen kommt man an die Stelle, an der diese Oberfläche
> gesteuert wird. Genau das macht dieses Projekt. Jeder kann das an seinem eigenen
> Gerät machen.

**→ [Read this in English](#english)**

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

## Wonach du wahrscheinlich gesucht hast

- **„Fire TV Stick Werbung entfernen"** / „Fire TV Werbung abschalten"
- **„Fire TV ohne Werbung nutzen"**, „Fire TV werbefrei einrichten"
- **„Amazon Fire TV Launcher ersetzen"**, „anderen Launcher auf Fire TV installieren"
- **„Projectivy Launcher Fire TV"** — Projectivy gibt es nur im Play Store, und den
  hat der Fire TV nicht. Dieses Projekt installiert sich direkt per `adb`
- **„Fire TV debloat"**, „Fire TV Amazon Apps deaktivieren", „Fire TV Tracking abschalten"
- **„Fire TV eigene Apps auf den Homescreen"**, „Fire TV Startbildschirm anpassen"
- **„Fire TV Stick 4K Plus Root"** — die ehrliche Antwort: es gibt keinen öffentlichen
  Unlock für diese Generation. Braucht man aber auch nicht.

Was dieses Projekt **nicht** ist: keine Firmware, kein Root-Tool, kein
Play-Store-Ersatz, kein Streaming-Dienst.

---

## Was es kann

| Funktion | Beschreibung |
|---|---|
| **Werbefreier Start** | Amazons Homescreen bleibt verdeckt, sobald der Fire TV startet oder Home gedrückt wird |
| **Live-TV** | 11 öffentlich-rechtliche Sender mit laufender Sendung, Fortschrittsbalken und Vorschau im Fenster |
| **Nachrichten** | Tagesschau, Tagesthemen und Deutschlandfunk auf Abruf (kein Live-Radio) |
| **App-Raster** | Installierte Apps auf einer Seite, Favoriten zuerst, Amazon-Apps ausgeblendet |
| **Uhr, Datum, Wetter** | Oben im Kopf; Wetter über Open-Meteo, ohne API-Key |
| **Art Mode nach 3 min** | Ohne Tastendruck werden Kunstbilder gezeigt, Bildschirm auf 15 % gedimmt |
| **Debloat-Anleitung** | Welche Amazon-Pakete sich abschalten lassen — und welche nicht |

---

## Wie die Sender und Podcasts laufen

Alle Quellen sind **DRM-frei** und wurden einzeln gegen HTTP 200 plus gültige
Playlist bzw. `audio/mpeg` geprüft.

**Live-TV (HLS):**

| Sender | Quelle |
|---|---|
| Das Erste, KiKA, ONE, ARD alpha | `*.ard-mcdn.de` (ARD) |
| ZDF, 3sat, ZDFinfo, ZDFneo, phoenix | `*.akamaized.net` (ZDF) |
| arte | `artesimulcast.akamaized.net` |
| tagesschau24 | `tagesschau.akamaized.net` |

**EPG:** `epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz` (XMLTV). Die Datei ist
entpackt ~25 MB — sie wird **zeilenweise gestreamt**, nur die 11 gewünschten Sender
werden behalten und lokal zwischengespeichert.

**Nachrichten:** ARD-Audiothek GraphQL (`api.ardaudiothek.de`) liefert die neuesten
Folgen mit direkter MP3-URL.

**Wetter:** Open-Meteo (`api.open-meteo.com`), kostenlos, kein Schlüssel.

> Die Streams werden nur abgespielt, nicht gespeichert oder weiterverbreitet. Für die
> Sendungsdaten gilt die Lizenz der jeweiligen Quelle.

---

## Wie der Home-Button überlistet wird

**Das ist der technisch interessante Teil.**

Fire OS erlaubt keinen zweiten Standard-Homescreen:

```
$ adb shell pm disable-user --user 0 com.amazon.tv.launcher
java.lang.SecurityException: Cannot disable a protected package: com.amazon.tv.launcher
```

Dasselbe bei `com.amazon.firehomestarter`, das den Home-Tastendruck abfängt.
Auch `pm set-home-activity`, `settings put secure preferred_home_activity` und
`android:priority` im `HOME`-Intent-Filter greifen nicht — Fire OS ignoriert sie.

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
  Sonderbehandlung legt das Overlay sich über die Einstellungen, das System holt sie
  zurück, das Overlay kommt wieder — eine Endlosschleife. Der Service erkennt
  `*Settings*`-Klassen und pausiert sich für 10 Minuten.
- **Ein Cooldown von 3 s** verhindert Mehrfach-Auslösung bei mehreren Events.
- **`am force-stop` beendet den Accessibility-Service**, und Fire OS trägt ihn danach
  *nicht* wieder ein. Deshalb setzt `deploy.sh` den Service nach jedem Install neu und
  **prüft nach**, statt zu hoffen.

Gut benutzbar, aber nicht unsichtbar: bei jedem App-Update schaltet Android den
Accessibility-Service ab und er muss neu gesetzt werden. Das ist Fire-OS-Politik,
nicht behebbar ohne Root.

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
└── BootReceiver.java        Service nach Neustart wieder eintragen
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

---
---

<a id="english"></a>

# Lean Launcher — Ad-Free Home Screen for Fire TV Stick 4K Plus

**An ad-free launcher replacement for the Amazon Fire TV Stick 4K Plus**
(Fire OS 8 / Android 11, tested on `AFTMA08C15`).

Amazon's Fire TV home screen shows banner ads, movie and series recommendations,
and rotating promo tiles. This launcher replaces it with only what you actually
need: **live TV, news, your own apps, clock, date and weather.**

Built **without root, without bootloader unlock, without custom firmware** —
just `adb`. Every change is reversible.

> **It's your TV. You decide what runs on it.**
>
> The device belongs to the buyer, not the manufacturer. A handful of `adb`
> commands get you to the layer that controls this interface. That's all this
> project does — and anyone can do it on their own device.

## Why this exists

Three wishes, nothing else:

1. **My channels, instantly.** TV turns on, one press, and I see the programme
   guide with what's on now, time remaining, and a live preview. No searching.
2. **My apps, instantly.** Everything I use at a glance — no scrolling, no row
   that runs off to the right.
3. **No ads for anything.** No banners, no Amazon recommendations, no tiles
   trying to sell me something.

## What you were probably searching for

- **"Fire TV Stick remove ads"**, "Fire TV disable ads", "ad-free Fire TV"
- **"Fire TV Stick 4K Plus ad free launcher"**
- **"replace Amazon Fire TV launcher"**, "install custom launcher on Fire TV"
- **"Projectivy Launcher Fire TV"** — Projectivy is Play Store only, and Fire TV
  has no Play Store. This project installs directly via `adb`
- **"Fire TV debloat"**, "disable Amazon apps on Fire TV", "Fire TV disable tracking"
- **"Fire TV custom home screen"**, "Fire TV show only my apps"
- **"Fire TV Stick 4K Plus root"** — honest answer: there is no public unlock for
  this generation. You don't need one either.

What this project is **not**: no firmware, no root tool, no Play Store
replacement, no streaming service.

## Features

| Feature | Description |
|---|---|
| **Ad-free home** | Amazon's home screen stays hidden on boot and on Home press |
| **Live TV** | 11 German public broadcasters with current show, progress bar, live preview |
| **News** | Tagesschau, Tagesthemen, Deutschlandfunk on demand (not live radio) |
| **App grid** | Installed apps on one page, favourites first, Amazon apps filtered out |
| **Clock, date, weather** | In the header; weather via Open-Meteo, no API key |
| **Art mode after 3 min** | Shows paintings when idle, screen dimmed to 15 % |
| **Debloat guide** | Which Amazon packages are safe to disable — and which are not |

## How the Home button is bypassed

**This is the technically interesting part.**

Fire OS does not allow a second default home screen:

```
$ adb shell pm disable-user --user 0 com.amazon.tv.launcher
java.lang.SecurityException: Cannot disable a protected package: com.amazon.tv.launcher
```

Same for `com.amazon.firehomestarter`, which intercepts the Home key. Neither
`pm set-home-activity`, nor `settings put secure preferred_home_activity`, nor
`android:priority` in the `HOME` intent filter works — Fire OS simply ignores them.

What does work: an **accessibility service** is allowed to draw an overlay on top
of everything. As soon as Amazon's `HomeActivity` comes to the foreground, the
overlay appears and our own home screen launches in the same moment. Once it has
drawn, the overlay disappears — you only ever see your own interface.

```
Amazon HomeActivity appears
        ↓
black overlay becomes visible
        ↓
own HomeActivity starts
        ↓
HomeActivity reports it is drawn
        ↓
overlay disappears
```

Three things that needed care:

- **Fire TV settings run INSIDE the Amazon launcher package.** Without special
  handling the overlay covers the settings, the system brings them back, the
  overlay returns — an infinite loop. The service detects `*Settings*` classes
  and pauses itself for 10 minutes.
- **A 3 second cooldown** prevents duplicate triggering from multiple events.
- **`am force-stop` kills the accessibility service**, and Fire OS does *not*
  re-register it afterwards. That's why `deploy.sh` re-sets the service after
  every install and **verifies** it instead of hoping.

Usable, but not invisible: Android disables the accessibility service on every
app update and it has to be set again. That's Fire OS policy, not fixable
without root.

## Build

Requirements: JDK (17+), Android SDK with `platforms/android-35` and
`build-tools/` — no Android Studio, no Gradle needed.

```bash
./build.sh
```

Pipeline: `aapt2 → javac → d8 → zipalign → apksigner`.
A keystore is generated on first run; the password comes from `$KS_PASS`
(default: `localdev`).

## Install

Enable ADB debugging on the stick:
**Settings → My Fire TV → About** → press the device name 7× →
**Developer Options → ADB debugging on**.

```bash
./deploy.sh <firetv-ip>:5555
```

`deploy.sh` builds, installs, grants permissions, activates the accessibility
service and verifies it is actually bound. Without arguments `$FIRE_TV_IP` is used.

## Undo everything

```bash
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb shell pm uninstall io.github.leanlauncher
```

After that the Home button goes to Amazon again. Nothing on the system itself is
modified — every change is a setting that can be reverted.

## Code layout

```
app/src/io/github/leanlauncher/
├── HomeActivity.java        Home screen (header, tiles, app grid)
├── HomeService.java         Accessibility service, covers Amazon's home
├── ChannelListActivity.java Channel list with preview and EPG
├── LiveActivity.java        Full-screen playback with channel zapping
├── NewsActivity.java        News podcasts on demand
├── Epg.java                 XMLTV streaming + local cache
├── Podcasts.java            ARD Audiothek GraphQL
├── Weather.java             Open-Meteo with cache
├── Channels.java            Channel list + EPG IDs
├── Ui.java                  Colours and dimensions
└── BootReceiver.java        Re-register service after reboot
```

**Performance rules followed:**

- Views are built **once**. Changes only swap text and colour.
- No network on the UI thread. EPG and weather come from cache.
- Exactly **one** `VideoView`, reused for every channel.

## Debloat (optional)

The launcher replaces the interface, but Amazon services keep running in the
background. What is safe to disable — and what is not — is documented in
[`docs/debloat.md`](docs/debloat.md).

## Limitations

- **No root, no custom firmware.** The bootloader is signed; there is no public
  unlock for this generation. That's a property of the device, not a limitation
  of this project.
- **Amazon's launcher keeps running in the background** (~130 MB) because it
  cannot be uninstalled.
- **After every update** `deploy.sh` must be run again.
- **ARD and ZDF URLs change.** If a channel stays black, the stream URL is
  usually stale and needs to be looked up again.

## License

MIT — do whatever you want with it.

---

**Keywords:** Fire TV Stick 4K Plus, Fire TV Stick, Amazon Fire TV, Fire OS 8,
ad-free launcher, remove ads Fire TV, custom launcher Fire TV, alternative launcher,
debloat Fire TV, disable Amazon apps, no root launcher, ADB launcher, Android TV
launcher, live TV launcher, German public broadcasting, ARD, ZDF, EPG, XMLTV,
werbefrei, Werbung entfernen, Startbildschirm anpassen, Projectivy Alternative
