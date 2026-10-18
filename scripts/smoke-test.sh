#!/usr/bin/env bash
# Installs the APK on a running emulator, opens the app, feeds it a YouTube link and
# fails if the app crashes. Prints the crash log and what is on screen.
set -u
PKG=com.animator222.ytdownloader
APK="$1"
URL="${2:-https://www.youtube.com/watch?v=jNQXAC9IVRw}"

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | grep -v 'text=""' | sed 's/^text=//'
}

adb install -r "$APK" || exit 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb logcat -c

echo "::group::Launch"
adb shell am start -W -n $PKG/.MainActivity
sleep 20
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am start -W -n $PKG/.MainActivity
sleep 10
echo "pid: $(adb shell pidof $PKG)"
echo "::endgroup::"

echo "::group::Engine version"
sleep 30
tap_desc() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1)
  [ -z "$b" ] && return 1
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | grep -o '[0-9]\+' | tr '\n' ' ')"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
if tap_desc Menu; then
  sleep 2
  dump_ui | grep -i "yt-dlp"
  adb shell input keyevent KEYCODE_BACK
fi
echo "::endgroup::"

echo "::group::Share link"
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "$URL" -n $PKG/.MainActivity
for i in $(seq 1 24); do
  sleep 5
  if dump_ui | grep -qE 'Choose quality|ERROR|YouTube|link'; then break; fi
done
echo "--- screen ---"
dump_ui
echo "::endgroup::"

echo "::group::Start download"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
bounds=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep 'text="Download"' | grep -o 'bounds="[^"]*"' | head -1)
if [ -n "$bounds" ]; then
  read -r x1 y1 x2 y2 <<<"$(echo "$bounds" | grep -o '[0-9]\+' | tr '\n' ' ')"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  for i in $(seq 1 36); do
    sleep 5
    if dump_ui | grep -qE 'Saved to gallery|Failed|ERROR'; then break; fi
  done
  echo "--- screen ---"
  dump_ui
  echo "--- gallery (MediaStore videos) ---"
  adb shell content query --uri content://media/external/video/media --projection _display_name:relative_path
else
  echo "Download button not visible (fetch probably failed); skipping download."
fi
echo "::endgroup::"

adb logcat -d > logcat.txt
echo "::group::App log"
grep -E "$PKG|AndroidRuntime|youtubedl|WM-" logcat.txt | tail -200
echo "::endgroup::"

if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "::error::App crashed"
  grep -A60 "FATAL EXCEPTION" logcat.txt
  exit 1
fi
if [ -z "$(adb shell pidof $PKG)" ]; then
  echo "::error::App is not running"
  exit 1
fi
echo "Smoke test passed"
