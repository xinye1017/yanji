#!/bin/bash
# 单次会话内完成：等设备 → 备份数据库 → 记录当前状态
# 注意：本环境里 adb daemon 会随子进程结束被重置，所以所有 adb 操作必须链在同一次执行里。
export MSYS_NO_PATHCONV=1
set -u

PKG=com.example.yanji
OUT="D:/AI项目/yanji/build/device-backup"
mkdir -p "$OUT"

adb start-server >/dev/null 2>&1

DEV=""
for i in $(seq 1 12); do
  DEV=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  [ -n "$DEV" ] && break
  sleep 2
done

if [ -z "$DEV" ]; then
  echo "RESULT: NO_DEVICE"
  adb devices -l
  exit 0
fi

echo "RESULT: DEVICE_OK"
echo "device=$DEV"
echo "--- device props ---"
adb -s "$DEV" shell getprop ro.product.model
adb -s "$DEV" shell getprop ro.build.version.release
adb -s "$DEV" shell getprop ro.build.version.sdk

echo "--- installed app ---"
adb -s "$DEV" shell pm list packages | grep -i yanji || echo "(not installed)"
adb -s "$DEV" shell dumpsys package "$PKG" | grep -E "versionCode=|versionName=|lastUpdateTime=" | head -5

echo "--- databases dir ---"
adb -s "$DEV" shell run-as "$PKG" ls -l databases/ 2>&1

echo "--- pulling database backup ---"
for f in yanji_study.db yanji_study.db-wal yanji_study.db-shm; do
  if adb -s "$DEV" shell run-as "$PKG" ls "databases/$f" >/dev/null 2>&1; then
    adb -s "$DEV" exec-out run-as "$PKG" cat "databases/$f" > "$OUT/$f" 2>/dev/null
    echo "pulled $f -> $(wc -c < "$OUT/$f") bytes"
  else
    echo "absent $f"
  fi
done

echo "--- no_backup dir (凭据文件，预期不存在或为空) ---"
adb -s "$DEV" shell run-as "$PKG" ls -l no_backup/ 2>&1

echo "--- prefs ---"
adb -s "$DEV" shell run-as "$PKG" ls -l shared_prefs/ 2>&1

echo "DONE"
