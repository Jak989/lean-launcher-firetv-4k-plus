#!/bin/bash
# Lean Launcher - Ein-Block-Installation fuer macOS
#
# Aufruf:
#   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Jak989/lean-launcher-firetv-4k-plus/main/install-macos.sh)" -- 192.168.1.42
#
# Das Skript ist absichtlich defensiv: es prueft jeden Schritt und bricht mit
# einer verstaendlichen Meldung ab, statt blind weiterzulaufen.

set -u

REPO="Jak989/lean-launcher-firetv-4k-plus"
VERSION="v1.0"
APK_NAME="lean-launcher-firetv-4k-plus-${VERSION}.apk"
PKG="io.github.leanlauncher"
SVC="${PKG}/${PKG}.HomeService"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }
warn() { printf "  \033[33m!\033[0m %s\n" "$1"; }
die()  { printf "\n  \033[31m✗ %s\033[0m\n\n" "$1"; exit 1; }

bold "Lean Launcher — Installation für Fire TV Stick 4K Plus"
echo

# ---------------------------------------------------------------- 1. IP pruefen
IP="${1:-}"
if [ -z "$IP" ]; then
  echo "  Aufruf:"
  echo "    /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/${REPO}/main/install-macos.sh)\" -- DEINE-IP"
  echo
  echo "  Die IP findest du am Fire TV unter:"
  echo "    Einstellungen > My Fire TV > Info > Netzwerk"
  echo
  die "Keine IP angegeben."
fi

# Port ergaenzen falls nicht vorhanden
case "$IP" in
  *:*) TARGET="$IP" ;;
  *)   TARGET="${IP}:5555" ;;
esac

ok "Ziel: $TARGET"

# ---------------------------------------------------------------- 2. adb finden
ADB=""
for c in "$(command -v adb 2>/dev/null)" \
         "$HOME/Library/Android/sdk/platform-tools/adb" \
         "/opt/homebrew/bin/adb" \
         "/usr/local/bin/adb"; do
  if [ -n "$c" ] && [ -x "$c" ]; then ADB="$c"; break; fi
done

if [ -z "$ADB" ]; then
  warn "adb nicht gefunden — wird installiert"
  if ! command -v brew >/dev/null 2>&1; then
    die "Homebrew fehlt. Bitte zuerst installieren:
    /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"
  Danach dieses Skript erneut ausführen."
  fi
  brew install --cask android-platform-tools >/dev/null 2>&1 || die "adb-Installation fehlgeschlagen."
  ADB="$(command -v adb 2>/dev/null)"
  [ -n "$ADB" ] || ADB="/opt/homebrew/bin/adb"
  [ -x "$ADB" ] || die "adb wurde installiert, ist aber nicht auffindbar."
fi
ok "adb: $ADB"

# ---------------------------------------------------------------- 3. APK holen
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

bold "Lade App herunter ..."
URL="https://github.com/${REPO}/releases/download/${VERSION}/${APK_NAME}"
curl -fsSL --retry 2 -o "$TMPDIR/app.apk" "$URL" || die "Download fehlgeschlagen: $URL"
SIZE=$(wc -c < "$TMPDIR/app.apk" | tr -d ' ')
[ "$SIZE" -gt 10000 ] || die "Heruntergeladene Datei ist zu klein ($SIZE Bytes) — Download unvollständig."
ok "App geladen ($SIZE Bytes)"

# ---------------------------------------------------------------- 4. verbinden
bold "Verbinde mit dem Fire TV ..."
"$ADB" start-server >/dev/null 2>&1
OUT="$("$ADB" connect "$TARGET" 2>&1)"
case "$OUT" in
  *"connected to"*) ok "Verbunden" ;;
  *"already connected"*) ok "Bereits verbunden" ;;
  *) die "Verbindung fehlgeschlagen: $OUT
  Prüfe: ADB-Debugging an? Richtige IP?" ;;
esac

# Auf Autorisierung warten - hier muss der Nutzer am TV bestaetigen
STATE="$("$ADB" -s "$TARGET" get-state 2>&1 | tr -d '\r')"
if [ "$STATE" != "device" ]; then
  echo
  bold "  → Am Fire TV erscheint jetzt ein Dialog:"
  echo "      \"Zulassen, dass der Computer debuggt?\""
  echo "      Häkchen bei \"Immer zulassen\" setzen, dann OK"
  echo
  printf "  Warte auf Bestätigung "
  for i in $(seq 1 60); do
    sleep 2
    STATE="$("$ADB" -s "$TARGET" get-state 2>&1 | tr -d '\r')"
    if [ "$STATE" = "device" ]; then echo; break; fi
    printf "."
  done
  echo
  [ "$STATE" = "device" ] || die "Keine Bestätigung am Fernseher. Dialog verpasst? Skript erneut starten."
fi
ok "Gerät autorisiert"

# ---------------------------------------------------------------- 5. installieren
bold "Installiere Lean Launcher ..."
INST="$("$ADB" -s "$TARGET" install -r "$TMPDIR/app.apk" 2>&1)"
case "$INST" in
  *Success*) ok "Installiert" ;;
  *INSTALL_FAILED_ALREADY_EXISTS*) die "App ist bereits installiert. Erst deinstallieren? Nein — Skript nutzt -r, das sollte nicht passieren." ;;
  *) die "Installation fehlgeschlagen: $INST" ;;
esac

# ---------------------------------------------------------------- 6. Service setzen
bold "Aktiviere den Home-Button-Service ..."
"$ADB" -s "$TARGET" shell "settings put secure enabled_accessibility_services $SVC" >/dev/null 2>&1
"$ADB" -s "$TARGET" shell "settings put secure accessibility_enabled 1" >/dev/null 2>&1

# Nachpruefen - Fire OS ignoriert das manchmal still
BOUND=0
for i in 1 2 3 4 5; do
  sleep 2
  CUR="$("$ADB" -s "$TARGET" shell "settings get secure enabled_accessibility_services" 2>/dev/null | tr -d '\r')"
  EN="$("$ADB" -s "$TARGET" shell "settings get secure accessibility_enabled" 2>/dev/null | tr -d '\r')"
  B="$("$ADB" -s "$TARGET" shell "dumpsys accessibility 2>/dev/null | grep -c leanlauncher" 2>/dev/null | tr -d '\r')"
  if [ "$CUR" = "$SVC" ] && [ "$EN" = "1" ] && [ "${B:-0}" != "0" ]; then
    BOUND=1
    break
  fi
done

if [ "$BOUND" = "1" ]; then
  ok "Service aktiv und gebunden"
else
  warn "Service noch nicht gebunden — Fire TV neu starten hilft"
fi

# ---------------------------------------------------------------- 7. starten
bold "Starte den Launcher ..."
"$ADB" -s "$TARGET" shell "am start -n ${PKG}/.HomeActivity" >/dev/null 2>&1
sleep 4

FRONT="$("$ADB" -s "$TARGET" shell "dumpsys activity activities 2>/dev/null | grep mResumedActivity | head -1" | sed 's/.*u0 //' | tr -d '\r')"

echo
if [ "$BOUND" = "1" ]; then
  bold "Fertig. 🎉"
  echo
  echo "  Der Launcher läuft. Ab jetzt:"
  echo "    • startet er automatisch beim Einschalten"
  echo "    • führt der Home-Button zu ihm"
  echo "    • bleibt alles auch nach einem Neustart"
  echo
  echo "  Aktuell im Vordergrund: ${FRONT:-unbekannt}"
  if ! printf '%s' "$FRONT" | grep -q leanlauncher; then
    warn "Der Launcher ist noch nicht vorne — drück am TV einmal Home."
  fi
else
  bold "Fast fertig — ein Schritt fehlt"
  echo
  echo "  Der Home-Button-Service ist gesetzt, aber Fire OS hat ihn noch nicht"
  echo "  angenommen. Das passiert manchmal beim ersten Mal."
  echo
  echo "  → Fire TV einmal aus- und wieder einschalten (Strom trennen ist nicht nötig)"
  echo "  → danach dieses Skript erneut starten"
fi
echo
echo "  Rückgängig machen:"
echo "    $ADB shell settings delete secure enabled_accessibility_services"
echo "    $ADB shell settings put secure accessibility_enabled 0"
echo "    $ADB shell pm uninstall $PKG"
echo
