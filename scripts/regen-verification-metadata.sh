#!/usr/bin/env bash
# Regenerate gradle/verification-metadata.xml over the COMPLETE dependency graph.
#
# WHY THIS EXISTS
#   Generating the metadata from an ordinary build under-covers the graph, because AGP's
#   internal instrumented-test configurations (e.g.
#   `:app:_internal-unified-test-platform-android-device-provider-ddmlib`) are only
#   resolved by connected-device tasks. CI builds on a cold cache, downloads those
#   descriptors, and fails with:
#
#     Dependency verification failed ... One artifact failed verification
#
#   Two extra flags are what make the result complete:
#     -I  scripts/lib/resolve-all-configurations.init.gradle  forces every resolvable
#         configuration to resolve, so UTP/lint/detached configurations are included.
#     --refresh-dependencies  re-fetches module descriptors that are already in the local
#         Gradle cache. Without it, cached descriptors are never re-verified and therefore
#         never recorded, and the file silently stays incomplete.
#
# REVIEW IS MANDATORY
#   This script is additive-only by construction: it aborts and restores the previous file
#   if any previously recorded checksum would be dropped or changed. It still cannot prove
#   the new checksums are correct, so it prints the added entries and requires you to
#   confirm them against the upstream repositories before committing. Tracked procedure:
#   docs/migration/repo-hardening-plan.md, "Change controls".
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

METADATA="gradle/verification-metadata.xml"
# Must stay RELATIVE and be passed after `cd "$ROOT"`. An absolute POSIX path such as
# /d/AI项目/yanji/... is mangled by gradlew.bat on Windows into a doubled path
# (D:\AI项目\yanji\d\AI项目\...) and Gradle rejects it as non-existent.
INIT_SCRIPT="scripts/lib/resolve-all-configurations.init.gradle"

if [ ! -f "$METADATA" ]; then
  echo "Missing $METADATA; nothing to regenerate." >&2
  exit 1
fi
if [ ! -f "$INIT_SCRIPT" ]; then
  echo "Missing $INIT_SCRIPT; cannot enumerate the full dependency graph." >&2
  exit 1
fi

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) GRADLEW="./gradlew.bat" ;;
  *)                    GRADLEW="./gradlew" ;;
esac

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cp "$METADATA" "$work/before.xml"

# Emit "group:name:version::artifact|sha256" for every recorded artifact, so the before/after
# comparison is immune to element ordering and line-ending differences.
extract() {
  awk -F'"' '
    /<component group=/ { component = $2 ":" $4 ":" $6; artifact = ""; next }
    /<artifact name=/   { artifact = $2; next }
    /<sha256 value=/    { print component "::" artifact "|" $2 }
  ' "$1" | LC_ALL=C sort
}

extract "$work/before.xml" > "$work/before.txt"

echo "Resolving the complete dependency graph and rewriting $METADATA ..."
"$GRADLEW" \
  --write-verification-metadata sha256 \
  --refresh-dependencies \
  --no-configuration-cache \
  -I "$INIT_SCRIPT" \
  help --stacktrace

extract "$METADATA" > "$work/after.txt"

removed="$(comm -23 "$work/before.txt" "$work/after.txt")"
added="$(comm -13 "$work/before.txt" "$work/after.txt")"

if [ -n "$removed" ]; then
  echo >&2
  echo "REFUSING the result: regeneration dropped or altered existing checksums." >&2
  echo "$removed" | sed 's/^/  removed-or-changed: /' >&2
  echo >&2
  echo "Metadata regeneration must be additive. Restoring the previous file." >&2
  cp "$work/before.xml" "$METADATA"
  exit 1
fi

added_count="$(printf '%s' "$added" | grep -c . || true)"
before_count="$(wc -l < "$work/before.txt" | tr -d ' ')"
after_count="$(wc -l < "$work/after.txt" | tr -d ' ')"

echo
echo "Artifacts recorded: $before_count -> $after_count (added $added_count, removed 0)"

if [ "$added_count" -eq 0 ]; then
  echo "Already complete for the current graph; no metadata change."
  exit 0
fi

echo
echo "Newly added entries (review each against the upstream repository before committing):"
printf '%s\n' "$added" | sed 's/^/  /'
echo
echo "Do NOT commit these as trusted facts just because Gradle emitted them. Diff with:"
echo "  git diff -- gradle/verification-metadata.xml"
echo
echo "Cross-check each new artifact by downloading it from Maven Central or Google Maven"
echo "and recomputing its SHA-256, then commit the reviewed result."
