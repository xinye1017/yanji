#!/bin/bash
# 构建产物清理：按需回收磁盘占用，不触碰源码、资源与真机数据。
#
# 为什么不随每次构建自动清：本仓库 §二.5 明确「严禁盲目 clean」——clean 会打掉
# Configuration Cache 与增量缓存，把 30 秒的增量构建变成数分钟的全量冷编译。
# 本脚本只做「空间换构建速度」的手动动作，且分三级：
#   默认（无参数）  仅清理 build/ 下的一次性诊断产物（带时间戳的目录 + 过期报告/截图），
#                   零构建代价，随时可跑。
#   --native        额外清理 node_modules 里 RN 原生库的 NDK/CMake 产物
#                   （react-native-reanimated / react-native-worklets 的 build/ 与 .cxx）。
#                   代价：下一次 assembleDebug 需重新编译这两个库的 C++（数分钟）。
#   --all           额外清理 app/build/intermediates 与 app/.cxx。代价同上。
#
# 用法：
#   scripts/clean-build-artifacts.sh            # 只清一次性产物
#   scripts/clean-build-artifacts.sh --native   # 再清原生库产物
#   scripts/clean-build-artifacts.sh --all      # 再清 app 侧中间产物
set -u
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEEP_STAMPED="${YANJI_KEEP_STAMPED:-3}"     # build/ 下带时间戳目录保留份数
REPORT_DAYS="${YANJI_REPORT_MAX_DAYS:-14}"   # 报告 / 截图保留天数

mode="reports"
for arg in "$@"; do
  case "$arg" in
    --native) mode="native" ;;
    --all)    mode="all" ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "UNKNOWN_ARG: $arg" >&2; exit 2 ;;
  esac
done

# 只统计这些路径的占用，不扫描整个仓库（node_modules 全量 du 很慢）。
tracked_paths() {
  printf '%s\n' \
    "$ROOT/node_modules/react-native-reanimated/android/build" \
    "$ROOT/node_modules/react-native-reanimated/android/.cxx" \
    "$ROOT/node_modules/react-native-worklets/android/build" \
    "$ROOT/node_modules/react-native-worklets/android/.cxx" \
    "$ROOT/app/build/intermediates" \
    "$ROOT/app/build/outputs" \
    "$ROOT/app/.cxx" \
    "$ROOT/build"
}

size_mb() {
  local total=0 p sz
  while IFS= read -r p; do
    [ -e "$p" ] || continue
    sz="$(du -sm "$p" 2>/dev/null | awk '{print $1}')"
    total=$((total + ${sz:-0}))
  done
  printf '%s\n' "$total"
}

echo "TRACKED_MB_BEFORE: $(size_mb)"
echo "MODE: $mode"

# --- 1. 一次性诊断产物：build/ 下带时间戳的目录，只保留最近 KEEP_STAMPED 份 ---
if [ -d "$ROOT/build" ]; then
  find "$ROOT/build" -mindepth 1 -maxdepth 1 -type d \
       -regextype posix-extended -regex '.*-[0-9]{8,}.*' -printf '%T@ %p\n' 2>/dev/null \
    | sort -rn | tail -n "+$((KEEP_STAMPED + 1))" | cut -d' ' -f2- \
    | while IFS= read -r dir; do
        [ -n "$dir" ] || continue
        # 真机备份永远不删（§七 决策表：device-backup/ 绝不提交也不可丢）
        case "$dir" in *device-backup*) continue ;; esac
        echo "REMOVE_STAMPED: $dir"
        rm -rf "$dir"
      done

  # 报告 / 截图按天过期
  for sub in orca-reports reports screenshots; do
    [ -d "$ROOT/build/$sub" ] || continue
    find "$ROOT/build/$sub" -type f -mtime "+$REPORT_DAYS" -print -delete 2>/dev/null | sed 's/^/REMOVE_OLD: /'
  done
fi

# --- 2. 原生库产物（--native / --all 都包含）---
if [ "$mode" = "native" ] || [ "$mode" = "all" ]; then
  for p in \
    "$ROOT/node_modules/react-native-reanimated/android/build" \
    "$ROOT/node_modules/react-native-reanimated/android/.cxx" \
    "$ROOT/node_modules/react-native-worklets/android/build" \
    "$ROOT/node_modules/react-native-worklets/android/.cxx"; do
    [ -e "$p" ] || continue
    echo "REMOVE_NATIVE: $p"
    rm -rf "$p"
  done
fi

# --- 3. app 侧中间产物（仅 --all）---
if [ "$mode" = "all" ]; then
  for p in "$ROOT/app/build/intermediates" "$ROOT/app/.cxx"; do
    [ -e "$p" ] || continue
    echo "REMOVE_APP: $p"
    rm -rf "$p"
  done
fi

echo "TRACKED_MB_AFTER: $(size_mb)"
echo "DONE"
