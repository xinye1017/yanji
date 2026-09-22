#!/bin/bash
# 单次会话内完成：安装 → 启动 → 等迁移 → 校验数据库 → 抓日志
export MSYS_NO_PATHCONV=1
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="${YANJI_PACKAGE:-com.example.yanji}"
APK="${YANJI_APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
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
[ -z "$DEV" ] && { echo "RESULT: NO_DEVICE"; exit 0; }

echo "device=$DEV"

echo "--- force stop old process ---"
"$ADB_BIN" -s "$DEV" shell am force-stop "$PKG"

echo "--- clear logcat buffer ---"
"$ADB_BIN" -s "$DEV" logcat -c

echo "--- install ---"
# Git Bash 里 adb.exe 无法 stat `/d/AI项目/...` 这种 POSIX 绝对路径（第 3 行的
# MSYS_NO_PATHCONV=1 关掉了自动转换），旧实现又不检查退出码 —— 结果是手机上装的还是旧包，
# 脚本却照样打印 DONE。故：优先传相对仓库根的路径，其次退回 cygpath 转换，且必须看到 Success。
cd "$ROOT" || { echo "RESULT: BAD_ROOT ($ROOT)"; exit 1; }
APK_ARG="${APK#"$ROOT"/}"
if [ "$APK_ARG" = "$APK" ] && command -v cygpath >/dev/null 2>&1; then
  APK_ARG="$(cygpath -w "$APK")"
fi
[ -f "$APK" ] || { echo "RESULT: APK_NOT_FOUND ($APK)"; exit 1; }
INSTALL_OUT="$("$ADB_BIN" -s "$DEV" install -r -d "$APK_ARG" 2>&1)"
echo "$INSTALL_OUT"
case "$INSTALL_OUT" in
  *Success*) echo "install: OK (arg=$APK_ARG)" ;;
  *) echo "RESULT: INSTALL_FAILED"; exit 1 ;;
esac

echo "--- launch ---"
"$ADB_BIN" -s "$DEV" shell am start -n "$PKG/.MainActivity" 2>&1

echo "--- waiting for migration to run (12s) ---"
sleep 12

echo "--- pid ---"
"$ADB_BIN" -s "$DEV" shell pidof "$PKG" 2>&1

echo "--- post-migration database state ---"
"$ADB_BIN" -s "$DEV" shell run-as "$PKG" ls -l databases/ 2>&1
for f in yanji_study.db yanji_study.db-wal yanji_study.db-shm; do
  "$ADB_BIN" -s "$DEV" exec-out run-as "$PKG" cat "databases/$f" > "$OUT/after-$f" 2>/dev/null && \
    echo "pulled after-$f -> $(wc -c < "$OUT/after-$f") bytes"
done

echo "--- no_backup dir (凭据) ---"
"$ADB_BIN" -s "$DEV" shell run-as "$PKG" ls -l no_backup/ 2>&1

echo "--- logcat: crashes / our tags ---"
"$ADB_BIN" -s "$DEV" logcat -d -v brief 2>&1 | grep -E "FATAL|AndroidRuntime|Room|YanjiSecret|FocusTimer|YanjiAI|SQLite" | tail -40

echo "--- logcat: app process lines ---"
"$ADB_BIN" -s "$DEV" logcat -d -v brief 2>&1 | grep -E "yanji" | tail -20

echo "DONE"
