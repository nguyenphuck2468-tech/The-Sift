#!/usr/bin/env bash
set -euo pipefail

if [[ "${MINECRAFT_EULA_ACCEPTED:-}" != "true" ]]; then
  echo "Set the repository Actions variable MINECRAFT_EULA_ACCEPTED=true only after the repository owner has accepted the Minecraft EULA." >&2
  exit 2
fi

run_dir="run/paper-26.3"
console_log="build/paper-boot-console.log"
mkdir -p "$run_dir" build
printf 'eula=true\n' > "$run_dir/eula.txt"

fifo="$run_dir/console-input.fifo"
rm -f "$fifo"
mkfifo "$fifo"
exec 3<>"$fifo"
trap 'exec 3>&-; rm -f "$fifo"' EXIT

./gradlew --no-daemon runServer --console=plain <"$fifo" >"$console_log" 2>&1 &
server_pid=$!
done_seen=false
for _ in $(seq 1 300); do
  if grep -Fq 'Done (' "$console_log"; then
    done_seen=true
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [[ "$done_seen" != "true" ]]; then
  cat "$console_log"
  wait "$server_pid" || true
  echo "Paper 26.3 did not reach its Done marker within five minutes." >&2
  exit 1
fi

printf 'stop\n' >&3
wait "$server_pid"

for log in "$console_log" "$run_dir/logs/latest.log"; do
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
done

echo "Paper 26.3 reached Done and no unbound/parse/registry failure marker was found."
