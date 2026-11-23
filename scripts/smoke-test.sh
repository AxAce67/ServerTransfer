#!/usr/bin/env bash
# Starts real Paper servers of the given versions with the built plugin, converts a 1.x config,
# runs the console commands, pings the server itself, and checks the log for errors.
#
#   ./gradlew build && scripts/smoke-test.sh 1.20.5 1.21.4 26.3
#
# Server jars are downloaded from PaperMC or Purpur ("purpur-1.21.4"; checksums verified) into run/smoke/<version>/ and reused.
# Requires: java (21+), curl, python3. Set JAVA=/path/to/java to run the servers on a specific JDK.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGIN_JAR="$(ls "$ROOT"/build/libs/ServerTransfer-*.jar 2>/dev/null | head -1)"
UA="ServerTransfer-smoke-test"
JAVA="${JAVA:-java}"
PORT=25611

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}
md5() {
  if command -v md5sum >/dev/null; then md5sum "$1" | cut -d' ' -f1; else md5 -q "$1"; fi
}

# download <version> <target>: "1.21.4" is Paper, "purpur-1.21.4" is Purpur. Checksums are verified.
download() {
  local v="$1" target="$2" meta url sum actual
  echo "[$v] downloading..."
  if [[ "$v" == purpur-* ]]; then
    v="${v#purpur-}"
    meta="$(curl -fsSL -A "$UA" "https://api.purpurmc.org/v2/purpur/$v/latest")" || { echo "[$1] FAIL: unknown version"; return 1; }
    url="https://api.purpurmc.org/v2/purpur/$v/$(python3 -c 'import json,sys;print(json.load(sys.stdin)["build"])' <<<"$meta")/download"
    sum="$(python3 -c 'import json,sys;print(json.load(sys.stdin)["md5"])' <<<"$meta")"
    curl -fsSL -A "$UA" -o "$target.part" "$url" || { echo "[$1] FAIL: download"; return 1; }
    actual="$(md5 "$target.part")"
  else
    meta="$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$v/builds/latest")" || {
      echo "[$1] FAIL: unknown version"; return 1; }
    url="$(python3 -c 'import json,sys;print(json.load(sys.stdin)["downloads"]["server:default"]["url"])' <<<"$meta")"
    sum="$(python3 -c 'import json,sys;print(json.load(sys.stdin)["downloads"]["server:default"]["checksums"]["sha256"])' <<<"$meta")"
    curl -fsSL -A "$UA" -o "$target.part" "$url" || { echo "[$1] FAIL: download"; return 1; }
    actual="$(sha256 "$target.part")"
  fi
  [ "$actual" = "$sum" ] || { echo "[$1] FAIL: checksum mismatch"; rm -f "$target.part"; return 1; }
  mv "$target.part" "$target"
}
[ -n "$PLUGIN_JAR" ] || { echo "Build first: ./gradlew build"; exit 2; }
[ $# -gt 0 ] || { echo "usage: $0 <paper-version>..."; exit 2; }

wait_for() { # file pattern timeout
  local i
  for ((i = 0; i < $3; i++)); do
    grep -qE "$2" "$1" 2>/dev/null && return 0
    sleep 1
  done
  return 1
}

overall=0
for V in "$@"; do
  DIR="$ROOT/run/smoke/$V"
  rm -rf "$DIR/plugins/ServerTransfer"
  mkdir -p "$DIR/plugins/ServerTransfer"
  if [ ! -f "$DIR/paper.jar" ]; then
    download "$V" "$DIR/paper.jar" || { overall=1; continue; }
  fi
  rm -f "$DIR"/plugins/ServerTransfer-*.jar
  cp "$PLUGIN_JAR" "$DIR/plugins/"
  echo "eula=true" >"$DIR/eula.txt"
  printf 'online-mode=false\nview-distance=3\nsimulation-distance=3\nserver-port=%s\nmax-players=20\n' "$PORT" \
    >"$DIR/server.properties"
  # A 1.x config: the plugin must convert it on startup.
  cat >"$DIR/plugins/ServerTransfer/config.yml" <<YAML
language: en
gui-title: "&1Server Selector"
gui-size: 9
show-status: true
show-player-count: true
countdown-time: 3
give-join-item: true
join-item-material: "COMPASS"
join-item-slot: 8
servers:
  self:
    material: DIAMOND
    display_name: "&aSelf"
    slot: 0
    address: "127.0.0.1:$PORT"
    lore: ["&7Click"]
  dead:
    material: BARRIER
    display_name: "&cDead"
    slot: 1
    address: "127.0.0.1:1"
YAML

  : >"$DIR/cmd.txt"
  rm -f "$DIR/server.log"
  (cd "$DIR" && tail -f "$DIR/cmd.txt" | "$JAVA" -Xms512M -Xmx1G -jar paper.jar nogui >server.log 2>&1) &
  send() { echo "$1" >>"$DIR/cmd.txt"; sleep 2; }

  result="PASS"
  if ! wait_for "$DIR/server.log" 'Done \(' 300; then
    result="FAIL: server did not start"
  else
    for c in "st" "st help" "st list" "st add extra 127.0.0.1:$PORT 5" "st add extra x" "st add Bad! x" \
      "st list" "st remove extra" "st send @a self" "st send nobody self" "st self" "st reload"; do send "$c"; done
    sleep 3
  fi
  echo "stop" >>"$DIR/cmd.txt"
  wait_for "$DIR/server.log" 'Disabling ServerTransfer' 60
  sleep 4
  pkill -f "tail -f $DIR/cmd.txt" 2>/dev/null

  log="$DIR/server.plain.log"
  sed -E $'s/\x1b\\[[0-9;]*m//g' "$DIR/server.log" >"$log"
  cfg="$DIR/plugins/ServerTransfer/config.yml"
  if [ "$result" = "PASS" ]; then
    grep -q 'Enabling ServerTransfer' "$log" || result="FAIL: plugin not enabled"
    grep -q 'Converted config.yml from 1.x' "$log" || result="FAIL: 1.x config not converted"
    grep -q 'config-version: 2' "$cfg" || result="FAIL: converted config not saved"
    [ -f "$DIR/plugins/ServerTransfer/config-1.x.yml" ] || result="FAIL: no backup of the old config"
    grep -qE '● Self 0/20 · [0-9]+ ms' "$log" || result="FAIL: self ping not shown as online"
    grep -qE '● Dead offline' "$log" || result="FAIL: dead server not shown as offline"
    grep -q 'Added extra' "$log" || result="FAIL: add failed"
    grep -q 'already exists' "$log" || result="FAIL: duplicate add accepted"
    grep -q 'Invalid id' "$log" || result="FAIL: bad id accepted"
    grep -q 'Removed extra' "$log" || result="FAIL: remove failed"
    grep -q 'No players matched' "$log" || result="FAIL: send without players"
    grep -q 'Only players can do that' "$log" || result="FAIL: console join"
    grep -q 'Reloaded. 2 server' "$log" || result="FAIL: reload"
    grep -q 'Usage: See' "$log" && result="FAIL: old plugin loaded"
    errors="$(grep -cE 'Exception|SEVERE|\[ERROR\]|Error occurred|WARN\].*ServerTransfer' "$log")"
    [ "$errors" = "0" ] || result="FAIL: $errors error/warning lines in log"
  fi
  echo "[$V] $result"
  [ "$result" = "PASS" ] || overall=1
done
exit $overall
