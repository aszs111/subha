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
# سحب على الخرز (يمين الشاشة) لإسقاط خرزات
for i in 1 2 3 4 5; do adb shell input swipe 940 700 940 1500 450; sleep 0.4; done
sleep 1; shot 02_after_swipes
adb shell input swipe 940 700 940 1000 900 &
sleep 0.45; shot 03_mid_drop; wait; sleep 1
# الضغط على الذكر (يسار الشاشة)
tap_text "الأذكار ‹" || adb shell input tap 330 800; sleep 2; shot 04_adhkar
adb shell input keyevent 4; sleep 1
# الضغط على الخرز → شكل الخرزات
tap_text "الخرزات" || adb shell input tap 940 1200; sleep 2; shot 05_beads
tap_text "كهرمان" && sleep 1 && adb shell input keyevent 4 && sleep 1 && shot 05b_amber
tap_text "الإعدادات" || adb shell input tap 80 170; sleep 2; shot 06_settings
adb shell input keyevent 4; sleep 1
adb shell pidof $PKG || echo "APP DIED"
adb logcat -d | grep -A20 "FATAL EXCEPTION" | head -40 > $OUT/crash.txt
exit 0
