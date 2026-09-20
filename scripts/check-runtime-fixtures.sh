#!/usr/bin/env bash
# Prevent runtime sample/mock data from entering app/src/main.
# Allowlist entries are stable signatures: path|rule|normalized source line.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SOURCE_DIR="app/src/main"
ALLOWLIST="scripts/runtime-fixture-allowlist.txt"
findings="$(mktemp)"
allowed="$(mktemp)"
trap 'rm -f "$findings" "$allowed"' EXIT

normalize_matches() {
  local rule="$1"
  local regex="$2"
  shift 2
  grep -RInE --include='*.kt' --include='*.java' "$regex" "$@" 2>/dev/null \
    | awk -F: -v rule="$rule" '{
        path=$1
        line=$2
        content=$0
        sub(/^[^:]*:[0-9]+:/, "", content)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", content)
        print path "|" rule "|" content "\t" path ":" line ":" content
      }' >> "$findings" || true
}

normalize_matches "named-runtime-fixture" \
  'seedInitial|seedSample|fakeData|dummyData|sampleSessions|sampleJournal|sampleChat|demoRecords|mockRecords|getInitialFocusSessions|getInitialExamSessions' \
  "$SOURCE_DIR"

normalize_matches "hardcoded-bulk-insert" \
  '(insertAll|upsertAll|saveAll)[[:space:]]*\([[:space:]]*(listOf|mutableListOf|arrayListOf)[[:space:]]*\(' \
  "$SOURCE_DIR"

normalize_matches "fixture-shaped-list" \
  '(val|var)[[:space:]]+[A-Za-z0-9_]*(seed|sample|fake|dummy|demo|mock)[A-Za-z0-9_]*[[:space:]]*=[[:space:]]*(listOf|mutableListOf|arrayListOf)[[:space:]]*\(' \
  "$SOURCE_DIR"

# Initializers may create UserSettings only. Any business entity constructor here is suspicious.
initializer_files=()
while IFS= read -r file; do initializer_files+=("$file"); done < <(
  find "$SOURCE_DIR" -type f \( -name '*Initializer*.kt' -o -name '*Application*.kt' \) -print
)
if [ "${#initializer_files[@]}" -gt 0 ]; then
  normalize_matches "initializer-business-entity" \
    '(FocusSession|ExamSession|NoteEntry|ChatSession|ChatMessage|CheckIn|UnlockedAchievement|QuickStartPreset)Entity[[:space:]]*\(' \
    "${initializer_files[@]}"
fi

sort -u "$findings" -o "$findings"
sed -E '/^[[:space:]]*(#|$)/d; s/[[:space:]]+$//' "$ALLOWLIST" | sort -u > "$allowed"

status=0
while IFS=$'\t' read -r signature location; do
  [ -z "$signature" ] && continue
  if ! grep -Fqx -- "$signature" "$allowed"; then
    if [ "$status" -eq 0 ]; then
      echo "Runtime fixture guard found unreviewed production-source matches:"
    fi
    printf '  %s\n    allowlist signature: %s\n' "$location" "$signature"
    status=1
  fi
done < "$findings"

if [ "$status" -ne 0 ]; then
  echo "Delete runtime fixture data or add the exact reviewed signature to $ALLOWLIST with a reason."
  echo "Business terms such as Mock Exam / 模拟考试 are intentionally not matched."
  exit 1
fi

echo "Runtime fixture guard: PASS ($(wc -l < "$findings" | tr -d ' ') reviewed match(es))."
