#!/bin/bash
# 单次会话内完成：等设备 → 备份数据库 → 记录当前状态
# 注意：本环境里 adb daemon 会随子进程结束被重置，所以所有 adb 操作必须链在同一次执行里。
export MSYS_NO_PATHCONV=1
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="${YANJI_PACKAGE:-com.example.yanji}"
OUT="${YANJI_DEVICE_BACKUP_DIR:-$ROOT/build/device-backup}"
ADB_BIN="${ADB:-adb}"
mkdir -p "$OUT"

"$ADB_BIN" start-server >/dev/null 2>&1

DEV=""
for i in $(seq 1 12); do
  DEV=$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  [ -n "$DEV" ] && break
  sleep 2
done

if [ -z "$DEV" ]; then
  echo "RESULT: NO_DEVICE"
  "$ADB_BIN" devices -l
  exit 0
fi

echo "RESULT: DEVICE_OK"
echo "device=$DEV"
echo "--- device props ---"
"$ADB_BIN" -s "$DEV" shell getprop ro.product.model
"$ADB_BIN" -s "$DEV" shell getprop ro.build.version.release
"$ADB_BIN" -s "$DEV" shell getprop ro.build.version.sdk

echo "--- installed app ---"
"$ADB_BIN" -s "$DEV" shell pm list packages | grep -i yanji || echo "(not installed)"
"$ADB_BIN" -s "$DEV" shell dumpsys package "$PKG" | grep -E "versionCode=|versionName=|lastUpdateTime=" | head -5

echo "--- databases dir ---"
"$ADB_BIN" -s "$DEV" shell run-as "$PKG" ls -l databases/ 2>&1

echo "--- pulling database backup ---"
for f in yanji_study.db yanji_study.db-wal yanji_study.db-shm; do
  if "$ADB_BIN" -s "$DEV" shell run-as "$PKG" ls "databases/$f" >/dev/null 2>&1; then
    "$ADB_BIN" -s "$DEV" exec-out run-as "$PKG" cat "databases/$f" > "$OUT/$f" 2>/dev/null
    echo "pulled $f -> $(wc -c < "$OUT/$f") bytes"
  else
    echo "absent $f"
  fi
done

echo "--- no_backup dir (凭据文件，预期不存在或为空) ---"
"$ADB_BIN" -s "$DEV" shell run-as "$PKG" ls -l no_backup/ 2>&1

echo "--- prefs ---"
"$ADB_BIN" -s "$DEV" shell run-as "$PKG" ls -l shared_prefs/ 2>&1

echo "DONE"
