#!/bin/bash
set -x
OUT=shots; mkdir -p $OUT
PKG=com.adel.subha
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $OUT/ui.xml >/dev/null 2>&1
  b=$(python3 - "$1" <<'PY'
import re,sys
t=sys.argv[1]; s=open('shots/ui.xml',encoding='utf-8').read()
for n in re.findall(r'<node [^>]*>', s):
    if ('text="%s"'%t) in n or ('content-desc="%s"'%t) in n:
        x1,y1,x2,y2=map(int,re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n).groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
  [ -n "$b" ] && adb shell input tap $b; [ -n "$b" ]
}
adb shell settings put global hide_error_dialogs 1
adb install -r app.apk
adb shell am start -n $PKG/.MainActivity; sleep 8
shot 01_main
for i in 1 2 3 4 5 6; do adb shell input swipe 864 600 864 1500 500; sleep 0.3; done
sleep 1; shot 02_after_swipes
for i in 1 2 3; do tap_text "تسبيحة" || adb shell input tap 160 2050; sleep 0.5; done
adb shell input tap 160 2050; sleep 0.15; shot 02b_tap_mid; sleep 1; shot 02c_after_taps
adb shell input swipe 864 700 864 820 2500 &
sleep 1.6; shot 03_mid_drag; wait; sleep 1
for i in $(seq 1 14); do adb shell input swipe 864 600 864 1500 400; done
sleep 1; shot 04_near_target
for i in $(seq 1 6); do adb shell input swipe 864 600 864 1500 400; done
sleep 2.5; shot 05_auto_next
tap_text "الأذكار" || adb shell input tap 330 900; sleep 2; shot 06_adhkar
adb shell input keyevent 4; sleep 1
tap_text "الخرزات" || adb shell input tap 864 1200; sleep 2; shot 07_beads
tap_text "كهرمان" && sleep 1 && adb shell input keyevent 4 && sleep 1 && shot 08_amber
tap_text "الإعدادات" || adb shell input tap 80 170; sleep 2; shot 09_settings
adb shell input swipe 540 1800 540 700 400; sleep 1; shot 10_settings2
adb shell input keyevent 4; sleep 1
adb shell pidof $PKG || echo "APP DIED"
adb logcat -d | grep -A20 "FATAL EXCEPTION" | head -40 > $OUT/crash.txt
exit 0
