# Installation in einem Schritt (macOS)

Ein Block. Kopieren, einfügen, Enter. Der Rest läuft von selbst.

---

## Vorher am Fire TV (einmalig, 30 Sekunden)

1. **Einstellungen** öffnen
2. **My Fire TV** → **Info**
3. **7× auf „Fire TV Stick"** klicken (den Gerätenamen) → „Entwickleroptionen freigeschaltet"
4. **Zurück** → **Entwickleroptionen** → **ADB-Debugging: AN**
5. **Info** → **Netzwerk** → IP notieren (z. B. `192.168.1.42`)

---

## Dann am Mac: dieser eine Block

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Jak989/lean-launcher-firetv-4k-plus/main/install-macos.sh)" -- 192.168.1.42
```

`192.168.1.42` durch die IP aus Schritt 5 ersetzen. Fertig.

---

## Was der Block macht

| Schritt | Aktion |
|---|---|
| 1 | Prüft ob `adb` da ist — wenn nicht, installiert es Homebrew + Android Platform Tools |
| 2 | Lädt die APK aus dem GitHub-Release |
| 3 | Prüft die SHA-256-Prüfsumme |
| 4 | Verbindet sich mit dem Fire TV — **du bestätigst am TV einmal „Immer zulassen"** |
| 5 | Installiert die APK |
| 6 | Aktiviert den Home-Button-Service |
| 7 | Prüft ob es wirklich geklappt hat und sagt es dir |

**Am TV erscheint einmal ein Dialog** „Zulassen, dass der Computer debuggt?" →
Häkchen bei *Immer zulassen* → **OK**. Das ist die einzige Interaktion.

---

## Wenn etwas schiefgeht

| Meldung | Bedeutung | Lösung |
|---|---|---|
| `unauthorized` | Dialog am TV nicht bestätigt | Am TV bestätigen, Skript nochmal |
| `Connection refused` | ADB-Debugging aus | Schritt 4 wiederholen |
| `No route to host` | Falsche IP | IP im Fire-TV-Menü prüfen |
| `SERVICE NICHT GEBUNDEN` | Fire OS hat den Service nicht angenommen | Fire TV neu starten, Skript nochmal |
| `command not found: adb` | Installation fehlgeschlagen | Siehe unten |

### adb selbst installieren

```bash
brew install --cask android-platform-tools
```

Kein Homebrew? Dann zuerst:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

---

## Was danach gilt

- Der Launcher startet **automatisch beim Einschalten**
- Der **Home-Button** führt zu ihm
- Nach einem **Neustart** bleibt alles erhalten
- Nach einem **Update der App** muss der Block erneut laufen (Android-Sicherheitssperre)

## Alles rückgängig machen

```bash
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb shell pm uninstall io.github.leanlauncher
```

Danach ist der Home-Button wieder bei Amazon. Es wird nichts am System verändert.
