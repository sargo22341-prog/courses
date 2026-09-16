#!/usr/bin/env bash
# Lance les tests instrumentes sur l'appareil connecte (emulateur de la CI) avec adb, sans passer
# par connectedDebugAndroidTest : sur la CI, son moteur de test a deja affiche BUILD SUCCESSFUL
# alors que l'installation de l'APK avait echoue et qu'aucun test n'avait tourne.
# Echoue si l'installation echoue, si un test echoue ou si aucun test n'a ete execute.
# Usage : scripts/instrumented-tests.sh   (apres :app:assembleDebug et :app:assembleDebugAndroidTest)
# Avec plusieurs appareils branches, choisir le bon avec ANDROID_SERIAL.
set -euo pipefail

package=org.opensources.courses
root="$(dirname "$0")/.."
apks=("$root/app/build/outputs/apk/debug/app-debug.apk"
  "$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
report_dir="$root/app/build/reports/androidTests"
report="$report_dir/instrument.txt"
mkdir -p "$report_dir"

# sys.boot_completed peut passer a 1 avant que le gestionnaire de paquets reponde.
for _ in $(seq 1 60); do
  adb shell pm path android > /dev/null 2>&1 && break
  sleep 5
done

# Installation classique (pas incrementale), retentee : juste apres le demarrage, le systeme de
# l'emulateur a deja refuse une installation (NullPointerException dans StorageManagerService).
install() {
  local apk=$1
  for attempt in 1 2 3; do
    if adb install -r -t --no-incremental "$apk"; then
      return 0
    fi
    echo "Installation de $(basename "$apk") echouee (tentative $attempt)." >&2
    sleep 15
  done
  return 1
}
for apk in "${apks[@]}"; do
  install "$apk"
done

adb shell am instrument -w "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee "$report"

if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$report"; then
  echo "Tests instrumentes en echec ou non executes (sortie complete : $report)." >&2
  echo "Redemarrages de system_server : $(adb shell getprop sys.system_server.start_count)" >&2
  adb logcat -d -b crash >&2 || true
  exit 1
fi
