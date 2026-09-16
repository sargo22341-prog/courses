#!/usr/bin/env bash
# Demarre l'APK release minifie sur l'appareil connecte (emulateur de la CI) et verifie qu'il
# tourne encore quelques secondes plus tard : un probleme R8 qui ne se voit qu'a l'execution
# (classe retiree ou renommee alors qu'elle est atteinte par reflexion) le fait planter au demarrage.
# Usage : scripts/release-smoke-test.sh   (apres :app:assembleRelease et :app:assembleDebug)
# L'APK est signe avec la cle de debug : ce build n'est jamais publie.
# Avec plusieurs appareils branches, choisir le bon avec ANDROID_SERIAL.
set -euo pipefail

package=org.opensources.courses
root="$(dirname "$0")/.."
unsigned=$(ls "$root"/app/build/outputs/apk/release/*-release-unsigned.apk)
keystore="$HOME/.android/debug.keystore"
build_tools=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -n 1)
signed="$(mktemp -d)/courses-release-smoke.apk"

# Cle de debug creee par le build debug ; son mot de passe est public (« android »).
"$build_tools/apksigner" sign --ks "$keystore" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android --out "$signed" "$unsigned"

adb install -r "$signed"
adb logcat -c
adb shell am start -W -n "$package/.MainActivity"
sleep 10

crashes=$(adb logcat -d -b crash)
if ! adb shell pidof "$package" > /dev/null || grep -q "$package" <<< "$crashes"; then
  echo "L'APK release ne demarre pas :" >&2
  echo "$crashes" >&2
  exit 1
fi
echo "APK release demarre sans erreur."
