#!/bin/bash
# 单次会话内完成：安装 → 启动 → 等迁移 → 校验数据库 → 抓日志
export MSYS_NO_PATHCONV=1
set -u

PKG=com.example.yanji
APK="D:/AI项目/yanji/app/build/outputs/apk/debug/app-debug.apk"
OUT="D:/AI项目/yanji/build/device-backup"

adb start-server >/dev/null 2>&1
DEV=""
for i in $(seq 1 12); do
  DEV=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  [ -n "$DEV" ] && break
  sleep 2
done
[ -z "$DEV" ] && { echo "RESULT: NO_DEVICE"; exit 0; }

echo "device=$DEV"

echo "--- force stop old process ---"
adb -s "$DEV" shell am force-stop "$PKG"

echo "--- clear logcat buffer ---"
adb -s "$DEV" logcat -c

echo "--- install ---"
adb -s "$DEV" install -r "$APK" 2>&1

echo "--- launch ---"
adb -s "$DEV" shell am start -n "$PKG/.MainActivity" 2>&1

echo "--- waiting for migration to run (12s) ---"
sleep 12

echo "--- pid ---"
adb -s "$DEV" shell pidof "$PKG" 2>&1

echo "--- post-migration database state ---"
adb -s "$DEV" shell run-as "$PKG" ls -l databases/ 2>&1
for f in yanji_study.db yanji_study.db-wal yanji_study.db-shm; do
  adb -s "$DEV" exec-out run-as "$PKG" cat "databases/$f" > "$OUT/after-$f" 2>/dev/null && \
    echo "pulled after-$f -> $(wc -c < "$OUT/after-$f") bytes"
done

echo "--- no_backup dir (凭据) ---"
adb -s "$DEV" shell run-as "$PKG" ls -l no_backup/ 2>&1

echo "--- logcat: crashes / our tags ---"
adb -s "$DEV" logcat -d -v brief 2>&1 | grep -E "FATAL|AndroidRuntime|Room|YanjiSecret|FocusTimer|YanjiAI|SQLite" | tail -40

echo "--- logcat: app process lines ---"
adb -s "$DEV" logcat -d -v brief 2>&1 | grep -E "yanji" | tail -20

echo "DONE"
