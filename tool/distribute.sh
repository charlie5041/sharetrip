#!/usr/bin/env bash
#
# Sends a release build to the testers through Firebase App Distribution.
# Run by the distribute jobs in .github/workflows/ci.yml, after the build.
#
# USAGE
#   tool/distribute.sh android    # kmp/androidApp/build/outputs/apk/release/*.apk
#
# ENVIRONMENT
#   FIREBASE_SERVICE_ACCOUNT  the service account's JSON key
#   TESTER_GROUPS             comma-separated App Distribution group aliases
#   BEFORE                    the commit main was at before this push; the
#                             release notes list every commit since then
#   GITHUB_SHA, RUNNER_TEMP   set by GitHub Actions
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

platform="${1:-}"
case "$platform" in
  android)
    build=$(find kmp/androidApp/build/outputs/apk/release -maxdepth 1 -name '*.apk' -print -quit)
    # The Firebase Android app the Flutter app used; same id, same testers.
    app_id=1:307482465264:android:ec5f444450ac8de73d0335
    ;;
  *)
    echo "usage: tool/distribute.sh android" >&2
    exit 1
    ;;
esac

if [ -z "$build" ] || [ ! -f "$build" ]; then
  echo "error: no $platform build found; run the build first" >&2
  exit 1
fi

notes="$RUNNER_TEMP/release-notes.txt"
version="$(sed -n 's/^versionName=//p' kmp/version.properties)+$(sed -n 's/^versionCode=//p' kmp/version.properties)"
{
  echo "$version (${GITHUB_SHA::7})"
  echo
  # A new branch or a force push leaves BEFORE unknown; list just this commit.
  if git cat-file -e "${BEFORE:-}^{commit}" 2>/dev/null; then
    git log --no-merges --format='- %s' "$BEFORE..$GITHUB_SHA"
  else
    git log -1 --format='- %s'
  fi
} > "$notes"
cat "$notes"

# The self-hosted Mac keeps its files between runs, so the key must not
# outlive this script, whether the upload succeeds or not.
key="$RUNNER_TEMP/service-account.json"
trap 'rm -f "$key"' EXIT
(umask 077 && printf '%s' "$FIREBASE_SERVICE_ACCOUNT" > "$key")
export GOOGLE_APPLICATION_CREDENTIALS="$key"

firebase appdistribution:distribute "$build" \
  --app "$app_id" \
  --groups "$TESTER_GROUPS" \
  --release-notes-file "$notes"
