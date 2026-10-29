#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: check-paper-boot-logs.sh <console-log> <paper-server-log>" >&2
  exit 2
fi

console_log=$1
server_log=$2
for log in "$console_log" "$server_log"; do
  if [[ ! -f "$log" ]]; then
    echo "Expected Paper log was not written: $log" >&2
    exit 1
  fi
  for marker in 'Unbound' 'Failed to parse' 'Registry loading errors'; do
    if grep -Fq "$marker" "$log"; then
      echo "Paper boot log contains forbidden datapack failure marker '$marker': $log" >&2
      exit 1
    fi
  done
  if grep -Eiq 'Error occurred while enabling|Could not load' "$log"; then
    echo "Paper boot log reports a plugin load/enable failure: $log" >&2
    exit 1
  fi
done

grep -Fq 'Done (' "$console_log" || { echo "Paper did not reach Done." >&2; exit 1; }
grep -Fq '[TheSift] Enabling TheSift' "$console_log" || {
  echo "Paper reached Done without a TheSift enable line." >&2
  exit 1
}
grep -Fq 'Provider strict: false' "$console_log" || {
  echo "sift doctor did not confirm the CI fallback mode (providers.strict: false)." >&2
  exit 1
}
grep -Fq 'Worldgen provider placement failures: 0' "$console_log" || {
  echo "sift doctor did not report zero worldgen provider-placement failures." >&2
  exit 1
}

echo "Paper reached Done; TheSift enabled; sift doctor confirmed CI fallback mode and zero worldgen placement failures."
