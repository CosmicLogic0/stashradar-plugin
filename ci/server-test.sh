#!/usr/bin/env bash
# Starts a Minecraft server, waits for "Done (", runs optional console commands, stops it, and checks the log.
# Copied from LogicLib's CI and extended for Stash Radar.
# Usage: server-test.sh <server-dir> <expected-log-regex> [more-expected-regex...] -- <start command...>
# Env:   TEST_COMMANDS   console commands to run after startup, separated by ';' (e.g. "stashradar selftest")
#        CHECK_PATTERN   lines matching this (case-insensitive) must not contain exception/error/failed
set -u
DIR="$1"; shift
EXPECTS=()
while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do EXPECTS+=("$1"); shift; done
shift
cd "$DIR"
echo "eula=true" > eula.txt
rm -f console.in; mkfifo console.in
sleep infinity > console.in &
KEEP=$!
"$@" < console.in > server.log 2>&1 &
PID=$!
ok=0
for i in $(seq 1 900); do
  if grep -q 'Done (' server.log; then ok=1; break; fi
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  sleep 1
done
sleep 5
if [ "$ok" = "1" ] && [ -n "${TEST_COMMANDS:-}" ]; then
  IFS=';' read -ra CMDS <<< "$TEST_COMMANDS"
  for c in "${CMDS[@]}"; do
    echo ">>> console: $c"
    echo "$c" > console.in
    sleep 8
  done
fi
echo "stop" > console.in
for i in $(seq 1 120); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
kill "$PID" 2>/dev/null || true
kill "$KEEP" 2>/dev/null || true
echo "================ last 80 log lines ================"
tail -n 80 server.log
echo "==================================================="
if [ "$ok" != "1" ]; then echo "::error::Server did not finish starting"; exit 1; fi
for e in "${EXPECTS[@]}"; do
  if ! grep -Eq "$e" server.log; then echo "::error::Expected log line not found: $e"; exit 1; fi
done
CHECK="${CHECK_PATTERN:-logiclib|stash ?radar|stashradar}"
if grep -Ei "$CHECK" server.log | grep -Eiq "exception|error|could not load|failed"; then
  echo "::error::LogicLib / Stash Radar problem in the log:"; grep -Ei "$CHECK" server.log | grep -Ei "exception|error|could not load|failed"; exit 1
fi
echo "PASS: server started, LogicLib + Stash Radar loaded and every expected line was found, with no errors."
