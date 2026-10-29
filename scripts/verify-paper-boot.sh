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

# CI boots without content-provider plugins installed, so run the documented
# development/CI mode (README: "Set it to false only for development/CI").
# Strict mode would legitimately fail chunk decoration on every provider-mapped
# block and retry forever; vanilla fallback keeps the boot gate honest instead.
mkdir -p "$run_dir/plugins/TheSift"
printf 'providers:\n  strict: false\n' > "$run_dir/plugins/TheSift/config.yml"

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
  printf 'stop\n' >&3 || true
  for _ in $(seq 1 30); do
    if ! kill -0 "$server_pid" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$server_pid" 2>/dev/null; then kill "$server_pid" 2>/dev/null || true; fi
  wait "$server_pid" || true
  cat "$console_log"
  echo "Paper 26.3 did not reach its Done marker within five minutes." >&2
  exit 1
fi

printf 'sift doctor\n' >&3
doctor_seen=false
for _ in $(seq 1 30); do
  if grep -Fq 'Provider strict: false' "$console_log" \
      && grep -Fq 'Worldgen provider placement failures: 0' "$console_log"; then
    doctor_seen=true
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then break; fi
  sleep 1
done

printf 'stop\n' >&3
wait "$server_pid"

bash scripts/check-paper-boot-logs.sh "$console_log" "$run_dir/logs/latest.log"

if [[ "$doctor_seen" != "true" ]]; then
  echo "Paper reached Done, but sift doctor did not report the CI fallback mode and zero worldgen placement failures." >&2
  exit 1
fi
