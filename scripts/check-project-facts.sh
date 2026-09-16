#!/usr/bin/env bash
# Keep README's declared Android/Room facts synchronized with source-of-truth files.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_GRADLE="app/build.gradle.kts"
DB_FILE="app/src/main/java/com/example/yanji/data/db/YanjiDatabase.kt"
VERSIONS="gradle/libs.versions.toml"
README="README.md"

extract_number() {
  local pattern="$1"
  local file="$2"
  sed -nE "s/.*${pattern}[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p" "$file" | head -n 1
}

compile_sdk="$(extract_number 'compileSdk' "$APP_GRADLE")"
min_sdk="$(extract_number 'minSdk' "$APP_GRADLE")"
target_sdk="$(extract_number 'targetSdk' "$APP_GRADLE")"
version_code="$(extract_number 'versionCode' "$APP_GRADLE")"
version_name="$(sed -nE 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$APP_GRADLE" | head -n 1)"
room_schema="$(sed -nE 's/.*version[[:space:]]*=[[:space:]]*([0-9]+),.*/\1/p' "$DB_FILE" | head -n 1)"
room_library="$(sed -nE 's/^room[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$VERSIONS" | head -n 1)"

for value_name in compile_sdk min_sdk target_sdk version_code version_name room_schema room_library; do
  if [ -z "${!value_name}" ]; then
    echo "Unable to extract $value_name from project sources." >&2
    exit 1
  fi
done

expected="project-facts: compileSdk=$compile_sdk minSdk=$min_sdk targetSdk=$target_sdk versionCode=$version_code versionName=$version_name roomSchema=$room_schema roomLibrary=$room_library"
declared="$(sed -nE 's/.*(project-facts: [^-]+).*/\1/p' "$README" | head -n 1 | sed -E 's/[[:space:]]+$//')"

status=0
if [ "$declared" != "$expected" ]; then
  echo "README project facts are stale."
  echo "  expected: $expected"
  echo "  declared: ${declared:-<missing>}"
  status=1
fi

while IFS=: read -r line text; do
  declared_schema="$(printf '%s' "$text" | sed -nE 's/.*Room v([0-9]+).*/\1/p')"
  if [ -n "$declared_schema" ] && [ "$declared_schema" != "$room_schema" ]; then
    echo "README:$line declares Room v$declared_schema, but source schema is v$room_schema."
    status=1
  fi
done < <(grep -nE 'Room v[0-9]+' "$README" || true)

if [ "$status" -ne 0 ]; then
  exit 1
fi

echo "Project facts guard: PASS ($expected)."
