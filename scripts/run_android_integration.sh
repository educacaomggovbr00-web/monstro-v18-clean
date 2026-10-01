#!/usr/bin/env bash
# Keep test status and diagnostics in one shell: emulator-runner executes each
# line of its inline script separately.
set -euo pipefail

mkdir -p app/build/reports/device
adb logcat -c || true
test_result=0
./gradlew :app:connectedQaAndroidTest --no-daemon --stacktrace || test_result=$?
adb logcat -d -v threadtime > app/build/reports/device/logcat.txt || true
adb pull /sdcard/Android/media/com.monstro.v18.studio.qa/additional_test_output \
    app/build/reports/device/screenshots || true
exit "$test_result"
