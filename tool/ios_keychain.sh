#!/usr/bin/env bash
#
# Puts the iOS signing certificate in a keychain of its own for one CI job,
# and takes it away again afterwards. Run by the distribute-ios job in
# .github/workflows/ci.yml, on the self-hosted Mac.
#
# The runner runs as a background service, and a service cannot use the
# private keys in the login keychain: codesign fails with
# errSecInternalComponent. A keychain made here, unlocked with a password
# only this job knows, has no such limit.
#
# USAGE
#   tool/ios_keychain.sh setup      # create, import, put first in the search list
#   tool/ios_keychain.sh cleanup    # take it off the search list and delete it
#
# ENVIRONMENT
#   IOS_SIGNING_P12           the certificate and private key, base64 of a .p12
#   IOS_SIGNING_P12_PASSWORD  the password the .p12 was exported with
#   RUNNER_TEMP               set by GitHub Actions
#
set -euo pipefail

keychain="$RUNNER_TEMP/ci-signing.keychain-db"
p12="$RUNNER_TEMP/signing.p12"

# The user's keychain search list, one path per line.
search_list() {
  security list-keychains -d user | sed -e 's/^[[:space:]]*"//' -e 's/"$//'
}

# Replaces the search list with the paths on stdin.
set_search_list() {
  local paths=()
  while IFS= read -r path; do
    [ -n "$path" ] && paths+=("$path")
  done
  security list-keychains -d user -s ${paths[@]+"${paths[@]}"}
}

without_ours() {
  search_list | grep -v -x -F "$keychain" || true
}

cleanup() {
  without_ours | set_search_list
  if [ -f "$keychain" ]; then
    security delete-keychain "$keychain"
  fi
}

setup() {
  if [ -z "${IOS_SIGNING_P12:-}" ]; then
    echo "::warning::IOS_SIGNING_P12 is not set; signing with the login keychain, which works only while the runner runs in a Terminal"
    exit 0
  fi
  # A job cancelled before its cleanup leaves the keychain behind.
  cleanup

  local password
  password=$(openssl rand -hex 24)
  trap 'rm -f "$p12"' EXIT
  echo "$IOS_SIGNING_P12" | base64 --decode > "$p12"

  security create-keychain -p "$password" "$keychain"
  # No auto-lock during the build: the default locks after 5 minutes.
  security set-keychain-settings "$keychain"
  security unlock-keychain -p "$password" "$keychain"
  security import "$p12" -k "$keychain" -f pkcs12 \
    -P "${IOS_SIGNING_P12_PASSWORD:-}" -T /usr/bin/codesign -T /usr/bin/security
  # Lets codesign use the key without the dialog a service cannot answer.
  security set-key-partition-list -S apple-tool:,apple: -s -k "$password" "$keychain" > /dev/null

  # First, so that codesign finds the key here before the login keychain's
  # copy of the same certificate, which it cannot use.
  { echo "$keychain"; without_ours; } | set_search_list

  echo "Signing identities in the CI keychain:"
  security find-identity -v -p codesigning "$keychain"
}

case "${1:-}" in
  setup) setup ;;
  cleanup) cleanup ;;
  *) echo "usage: $0 setup|cleanup" >&2; exit 1 ;;
esac
