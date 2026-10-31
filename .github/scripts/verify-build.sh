#!/bin/bash
set -e
# Functional suites do not abort the script: every later suite, the upgrade checks and the screenshot gate still run.
# Any failed suite makes the final status red (see the end of this file).
FAILED_SUITES=""
capture() {
  mkdir -p app/build/reports/device/Download
  # Copy evidence only; recursive Download pulls include fixture/provider paths
  # whose transient permissions can make adb pull fail after tests passed.
  adb shell 'find /sdcard/Download -maxdepth 1 -type f -name "sfe-*.png"' | tr -d '\r' > /tmp/sfe-capture-list
  while IFS= read -r path; do
    [ -n "$path" ] && { adb pull "$path" app/build/reports/device/Download/ || echo "capture: skipped $path"; }
  done < /tmp/sfe-capture-list
}
mkdir -p app/build/reports/device
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew installDebug
# Warm the cold emulator: the first real launch can take >20s (dex2oat/JIT on a
# fresh boot) and outlast the tests' launch wait. Force-stop so tests see a clean start.
adb shell monkey -p com.stockfileexplorer.app -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 25
adb shell am force-stop com.stockfileexplorer.app
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.SavedLocationsTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.RemoteAndSearchTest -Pandroid.testInstrumentationRunnerArguments.capture=remote-search || { capture; adb logcat -d > app/build/reports/device/logcat-remote-search.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
# Verify the exact immediately previous public APK upgrades without uninstalling.
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-0.2.11.apk" https://github.com/manuelmania93-svg/FileExplorerControl/releases/download/v0.2.11/File-Explorer-Control-0.2.11.apk
out=$(adb install -r -d "$RUNNER_TEMP/sfe-0.2.11.apk"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r app/build/outputs/apk/debug/app-debug.apk); echo "$out"; echo "$out" | grep -q Success
adb shell dumpsys package com.stockfileexplorer.app | grep -E 'versionCode|versionName'
if [ "${1:-false}" = "true" ]; then exit 0; fi
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.GateFlowTest -Pandroid.testInstrumentationRunnerArguments.capture=gate || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night yes
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.GateFlowTest -Pandroid.testInstrumentationRunnerArguments.capture=gate-night || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
adb shell cmd uimode night no
adb shell am force-stop com.stockfileexplorer.app || true
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
adb push app/src/androidTest/assets/fixtures/sfe-fixture-report.pdf /sdcard/Download/sfe-fixture-report.pdf
adb push app/src/androidTest/assets/fixtures/sfe-fixture-notes.docx /sdcard/Download/sfe-fixture-notes.docx
adb push app/src/androidTest/assets/fixtures/sfe-fixture-todo.txt /sdcard/Download/sfe-fixture-todo.txt
adb push app/src/androidTest/assets/viewers/sample-picture.jpg /sdcard/Download/sfe-sample-picture.jpg
adb shell ls -la /sdcard/Download
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.DocumentVisibilitySmokeTest -Pandroid.testInstrumentationRunnerArguments.capture=portrait || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night yes
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.DocumentVisibilitySmokeTest -Pandroid.testInstrumentationRunnerArguments.capture=night || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
adb shell cmd uimode night no
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.SelectionBarTest -Pandroid.testInstrumentationRunnerArguments.capture=select || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.LauncherIconTest -Pandroid.testInstrumentationRunnerArguments.capture=icon || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.BackNavigationTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.TrashAndAppsTest -Pandroid.testInstrumentationRunnerArguments.capture=trash || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.OpenSpeedBaselineTest || echo "DIAGNOSTIC: speed probe failed (timing only, not a functional gate)"
adb pull /sdcard/Download/sfe-open-speed.txt app/build/reports/device/ || true
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.SortPersistenceTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.DiskSpaceRefreshTest || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
adb pull /sdcard/Download/sfe-disk-refresh.txt app/build/reports/device/ || true
capture
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.UserFlowsTest || { capture; adb logcat -d > app/build/reports/device/logcat-flows.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.IncomingFileTest -Pandroid.testInstrumentationRunnerArguments.capture=incoming || { capture; adb logcat -d > app/build/reports/device/logcat-incoming.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.BuiltInViewersTest -Pandroid.testInstrumentationRunnerArguments.capture=viewers || { capture; adb logcat -d > app/build/reports/device/logcat-viewers.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night yes
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.BuiltInViewersTest -Pandroid.testInstrumentationRunnerArguments.capture=viewers-night || { capture; adb logcat -d > app/build/reports/device/logcat-viewers-night.txt || true; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd uimode night no
# Evidence screenshots from the earlier suites are already pulled by capture above. Move them out of Download so the
# German layout suite (which scrolls the Download list for its fixtures) sees the folder it was written against.
adb shell 'mkdir -p /sdcard/sfe-evidence-moved && mv /sdcard/Download/sfe-*.png /sdcard/sfe-evidence-moved/ 2>/dev/null || true'
# German-locale layout run via per-app language: no reboot and no
# root, so the installed package and the all-files grant stay in
# place (the previous reboot-based approach lost the package).
# Diagnose package visibility, then guarantee it: the package
# intermittently disappears from PackageManager after the test
# classes finish (cause undetermined), so reinstall idempotently.
echo "packages before locale step: $(adb shell pm list packages | grep -i stockfileexplorer | tr -d '\r')"
./gradlew installDebug
adb shell appops set com.stockfileexplorer.app MANAGE_EXTERNAL_STORAGE allow
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales de-AT
adb shell am force-stop com.stockfileexplorer.app
echo "app locales: $(adb shell cmd locale get-app-locales com.stockfileexplorer.app | tr -d '\r')"
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.stockfileexplorer.app.GermanLayoutTest -Pandroid.testInstrumentationRunnerArguments.capture=german || { capture; FAILED_SUITES="$FAILED_SUITES line$LINENO"; }
capture
adb shell cmd locale set-app-locales com.stockfileexplorer.app --locales "" || true
# Upgrade from the immediately previous published version too.
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-0.2.9.apk" https://github.com/manuelmania93-svg/FileExplorerControl/releases/download/v0.2.9/File-Explorer-Control-0.2.9.apk
adb uninstall com.stockfileexplorer.app || true
out=$(adb install "$RUNNER_TEMP/sfe-0.2.9.apk"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r app/build/outputs/apk/debug/app-debug.apk); echo "$out"; echo "$out" | grep -q Success
# Upgrade path proof: install the actually released 0.2.0 APK, then
# upgrade-install this build over it (fails if the signer differs).
curl -fL --retry 3 -o "$RUNNER_TEMP/sfe-0.2.0.apk" https://github.com/manuelmania93-svg/StockFileExplorer/releases/download/v0.2.0/StockFileExplorer-0.2.0.apk
out=$(adb install -r -d "$RUNNER_TEMP/sfe-0.2.0.apk"); echo "$out"; echo "$out" | grep -q Success
out=$(adb install -r app/build/outputs/apk/debug/app-debug.apk); echo "$out"; echo "$out" | grep -q Success
adb shell dumpsys package com.stockfileexplorer.app | grep -E 'versionCode|versionName' 
capture
# The screenshots that prove the incoming-file, sort, extract and disk flows must exist: a skipped pull must not hide them.
missing=""
for f in sfe-flows-sort-4-after-rebuild.png sfe-flows-extract-5-done.png sfe-flows-chooser-2-text-viewer.png sfe-disk-1-before.png sfe-disk-3-after.png; do
  [ -s "app/build/reports/device/Download/$f" ] || { echo "MISSING required screenshot: $f"; missing="$missing $f"; }
done
[ -z "$missing" ] || { echo "Required screenshots missing:$missing"; FAILED_SUITES="$FAILED_SUITES missing-screenshots"; }
[ -z "$FAILED_SUITES" ] || { echo "FAILED functional suites (script lines):$FAILED_SUITES"; exit 1; }
