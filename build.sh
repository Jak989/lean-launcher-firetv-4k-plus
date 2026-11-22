#!/bin/bash
set -e
cd "$(dirname "$0")"

SDK="$HOME/Library/Android/sdk"
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
PLATFORM="$SDK/platforms/android-35"
[ -d "$PLATFORM" ] || PLATFORM=$(ls -d "$SDK"/platforms/* | sort -V | tail -1)
echo "build-tools: $BT"
echo "platform:    $PLATFORM"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
PATH="$JAVA_HOME/bin:$PATH"

# Keystore-Passwort: aus Umgebung oder Standard fuer lokale Builds
KS_PASS="${KS_PASS:-localdev}"

rm -rf build
mkdir -p build/flat build/gen build/classes build/dex

echo "== aapt2 compile"
"$BT/aapt2" compile --dir app/res -o build/flat/res.zip

echo "== aapt2 link"
"$BT/aapt2" link -o build/base.apk \
  -I "$PLATFORM/android.jar" \
  --manifest app/AndroidManifest.xml \
  --java build/gen \
  --min-sdk-version 21 --target-sdk-version 34 \
  build/flat/res.zip

echo "== javac"
find app/src -name '*.java' > build/sources.txt
find build/gen -name '*.java' >> build/sources.txt
javac -source 8 -target 8 -nowarn -encoding UTF-8 -Xlint:-options \
  -bootclasspath "$PLATFORM/android.jar" \
  -classpath "$PLATFORM/android.jar" \
  -d build/classes @build/sources.txt 2>&1 | grep -v 'bootstrap class path' || true

echo "== d8"
"$BT/d8" --release --min-api 21 --lib "$PLATFORM/android.jar" \
  --output build/dex $(find build/classes -name '*.class')

cp build/base.apk build/unsigned.apk
echo "== dex einpacken (WURZEL!)"
(cd build/dex && zip -q -j -u ../unsigned.apk classes.dex)

echo "== zipalign"
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

echo "== keystore"
if [ ! -f keystore-launcher.jks ]; then
  keytool -genkeypair -keystore keystore-launcher.jks -alias app -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Lean Launcher, C=DE" 2>/dev/null
fi

echo "== sign"
"$BT/apksigner" sign --ks keystore-launcher.jks --ks-pass pass:$KS_PASS --key-pass pass:$KS_PASS \
  --out build/lean-launcher.apk build/aligned.apk

echo
echo "================ PRUEFUNG ================"
"$BT/aapt2" dump badging build/lean-launcher.apk | head -8
echo "--- classes.dex im Wurzel (muss 1 sein):"
unzip -l build/lean-launcher.apk | awk '{print $4}' | grep -x 'classes.dex' | wc -l
echo "--- signatur:"
"$BT/apksigner" verify --verbose build/lean-launcher.apk | head -5
echo
echo "FERTIG: $(pwd)/build/lean-launcher.apk ($(du -h build/maus-tv.apk | cut -f1))"
