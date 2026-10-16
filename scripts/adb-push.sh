#!/usr/bin/env bash
# 单次会话内完成：mDNS 发现 → 保留数据安装 → 启动并确认主进程存活。
set -euo pipefail
# 禁止 MSYS 改写设备端参数；本地 APK 路径在安装前单独转换。
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="${YANJI_PACKAGE:-com.example.yanji}"
APK="${YANJI_APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
ADB_BIN="${ADB:-adb}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v "$ADB_BIN" >/dev/null 2>&1 || fail "找不到 ADB：$ADB_BIN"
[ -f "$APK" ] || fail "APK 不存在：$APK"
APK_ARG="$APK"
if command -v cygpath >/dev/null 2>&1; then
  APK_ARG="$(cygpath -w "$APK")"
fi

if ! "$ADB_BIN" start-server; then
  fail "ADB server 启动失败。"
fi

DEV=""
for ((attempt = 1; attempt <= 15; attempt++)); do
  if ! DEVICES="$("$ADB_BIN" devices)"; then
    fail "ADB 设备发现失败。"
  fi
  while read -r serial state _; do
    [ "$state" = "device" ] || continue
    if [ -n "${ANDROID_SERIAL:-}" ]; then
      [ "$serial" = "$ANDROID_SERIAL" ] || continue
    else
      [[ "$serial" == *_adb-tls-connect* ]] || continue
    fi
    DEV="$serial"
    break
  done <<< "${DEVICES//$'\r'/}"
  [ -n "$DEV" ] && break
  if ((attempt < 15)); then
    sleep 2
  fi
done
[ -n "$DEV" ] || fail "未发现在线目标设备（ANDROID_SERIAL=${ANDROID_SERIAL:-未设置，使用 mDNS}）；请检查无线调试与网络代理（见 ADB.md）。"
printf 'device=%s\n' "$DEV"

if ! INSTALL_OUT="$("$ADB_BIN" -s "$DEV" install -r -d "$APK_ARG" 2>&1)"; then
  printf '%s\n' "$INSTALL_OUT" >&2
  fail "APK 安装失败。"
fi
printf '%s\n' "$INSTALL_OUT"
INSTALL_OUT="${INSTALL_OUT//$'\r'/}"
[[ "$INSTALL_OUT" =~ (^|$'\n')Success($|$'\n') ]] || fail "ADB 未确认安装成功。"

if ! LAUNCH_OUT="$("$ADB_BIN" -s "$DEV" shell am start -W -n "$PKG/.MainActivity" 2>&1)"; then
  printf '%s\n' "$LAUNCH_OUT" >&2
  fail "主入口 Activity 启动失败。"
fi
printf '%s\n' "$LAUNCH_OUT"
LAUNCH_OUT="${LAUNCH_OUT//$'\r'/}"
# am start -W 超时也可能返回 0，必须检查启动状态。
[[ "$LAUNCH_OUT" =~ (^|$'\n')Status:[[:blank:]]+ok($|$'\n') ]] || fail "ADB 未确认主入口 Activity 启动成功。"

# 短暂观察同一主进程，避免把启动即崩或崩溃后重启报告为成功。
PID=""
for ((attempt = 1; attempt <= 3; attempt++)); do
  sleep 1
  if ! CURRENT_PID="$("$ADB_BIN" -s "$DEV" shell pidof "$PKG" 2>&1)"; then
    printf '%s\n' "$CURRENT_PID" >&2
    fail "启动后主进程未存活或设备已断开。"
  fi
  CURRENT_PID="${CURRENT_PID//$'\r'/}"
  [[ "$CURRENT_PID" =~ ^[0-9]+$ ]] || fail "无法确认应用主进程 PID。"
  [[ -z "$PID" || "$CURRENT_PID" = "$PID" ]] || fail "启动后应用主进程发生重启。"
  PID="$CURRENT_PID"
done
printf 'DONE: 安装、启动与主进程存活检查通过（pid=%s）。\n' "$PID"
