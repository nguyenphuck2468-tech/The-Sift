#!/usr/bin/env bash
set -euo pipefail

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT
console_log="$tmp_dir/console.log"
server_log="$tmp_dir/latest.log"

cat >"$console_log" <<'EOF'
Paper startup
[TheSift] Enabling TheSift v2.0.0-foundation
Done (39.324s)! For help, type "help"
Provider strict: true; dimension allowed: false
Worldgen provider placement failures: 0 (deferred chunk retries: 0; last: none)
EOF
printf '[Server thread/INFO]: plugin enabled\n' >"$server_log"
bash scripts/check-paper-boot-logs.sh "$console_log" "$server_log" >/dev/null

printf '[Server thread/ERROR]: Error occurred while enabling TheSift\n' >>"$server_log"
if bash scripts/check-paper-boot-logs.sh "$console_log" "$server_log" >/dev/null 2>&1; then
  echo "Expected boot-log checker to reject a plugin enable error even with Done present." >&2
  exit 1
fi

printf '[Server thread/ERROR]: Could not load plugin TheSift\n' >"$server_log"
if bash scripts/check-paper-boot-logs.sh "$console_log" "$server_log" >/dev/null 2>&1; then
  echo "Expected boot-log checker to reject a plugin load error even with Done present." >&2
  exit 1
fi

printf '[Server thread/INFO]: plugin enabled\n' >"$server_log"
sed -i '/Provider strict:/d' "$console_log"
if bash scripts/check-paper-boot-logs.sh "$console_log" "$server_log" >/dev/null 2>&1; then
  echo "Expected boot-log checker to reject a missing strict-lock doctor result." >&2
  exit 1
fi

echo "Paper boot log checker fixtures passed (healthy, enable failure, load failure, missing doctor status)."
