#!/bin/bash
# Installiert den Launcher und aktiviert den Home-Service ZUVERLAESSIG.
#
# WARUM: Android schaltet Accessibility-Services bei jedem App-Update ab.
# Ohne diesen Schritt landet der Home-Button wieder im Amazon-Launcher.
#
# Aufruf:  ./deploy.sh            (Standard-IP)
#          ./deploy.sh <ip>:5555  (andere IP)
set -e
cd "$(dirname "$0")"

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
D="${1:-$FIRE_TV_IP}"
PKG="io.github.leanlauncher"
SVC="$PKG/$PKG.HomeService"

if [ -z "$D" ]; then
  echo "Aufruf: ./deploy.sh <firetv-ip>:5555"
  echo "   (oder FIRE_TV_IP als Umgebungsvariable setzen)"
  exit 1
fi

$ADB connect "$D" >/dev/null 2>&1 || true
if ! $ADB -s "$D" get-state >/dev/null 2>&1; then
  echo "!! Kein Gerät unter $D erreichbar."
  echo "   Am Fire TV: Einstellungen > My Fire TV > Entwickleroptionen > ADB-Debugging an"
  exit 1
fi

echo "== Baue"
./build.sh 2>&1 | grep -E "FERTIG|Fehler:|error:" | tail -2

echo "== Installiere"
$ADB -s "$D" install -r build/lean-launcher.apk | tail -1

echo "== Service nach Install neu setzen"
$ADB -s "$D" shell "settings put secure enabled_accessibility_services $SVC"
$ADB -s "$D" shell "settings put secure accessibility_enabled 1"
sleep 2

echo "== Berechtigungen"
$ADB -s "$D" shell "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS" 2>/dev/null || true
$ADB -s "$D" shell "appops set $PKG SYSTEM_ALERT_WINDOW allow" 2>/dev/null || true

echo "== Home-Service aktivieren (mit Nachpruefung)"
OK=0
for i in 1 2 3 4 5; do
  $ADB -s "$D" shell "settings put secure enabled_accessibility_services $SVC"
  $ADB -s "$D" shell "settings put secure accessibility_enabled 1"
  sleep 2
  CUR=$($ADB -s "$D" shell "settings get secure enabled_accessibility_services" | tr -d '\r')
  EN=$($ADB -s "$D" shell "settings get secure accessibility_enabled" | tr -d '\r')
  if [ "$CUR" = "$SVC" ] && [ "$EN" = "1" ]; then
    echo "   OK (Versuch $i)"
    OK=1
    break
  fi
  echo "   Versuch $i: noch nicht gesetzt, warte ..."
  sleep 2
done

echo "== App starten"
# WICHTIG: KEIN "am force-stop" - das beendet auch den Accessibility-Service
# und Fire OS traegt ihn danach nicht wieder ein.
$ADB -s "$D" shell "am start -n $PKG/.HomeActivity" >/dev/null 2>&1
sleep 4

BOUND=$($ADB -s "$D" shell "dumpsys accessibility | grep -c leanlauncher" | tr -d '\r')
FRONT=$($ADB -s "$D" shell "dumpsys activity activities | grep mResumedActivity | head -1" | sed 's/.*u0 //')

echo
echo "== STATUS"
echo "   Service:  $($ADB -s "$D" shell "settings get secure enabled_accessibility_services" | tr -d '\r')"
echo "   A11y an:  $($ADB -s "$D" shell "settings get secure accessibility_enabled" | tr -d '\r')"
echo "   Gebunden: $BOUND"
echo "   Vorne:    $FRONT"

if [ "$BOUND" = "0" ]; then
  echo
  echo "!! SERVICE NICHT GEBUNDEN."
  echo "   Abhilfe: Fire TV neu starten, dann dieses Skript erneut ausfuehren."
  exit 1
fi
echo
echo "Fertig. Home-Button fuehrt zum Launcher."
