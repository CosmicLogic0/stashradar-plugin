#!/usr/bin/env bash
# Downloads the newest Paper server jar for a Minecraft version to ./paper.jar
set -euo pipefail
V="$1"
UA="CosmicLogic-CI/1.0 (https://github.com/CosmicLogic0)"
URL=$(curl -fsSL -H "User-Agent: $UA" "https://fill.papermc.io/v3/projects/paper/versions/${V}/builds" 2>/dev/null \
  | jq -r 'if type=="array" then (sort_by(.id) | last | .downloads["server:default"].url) else empty end' 2>/dev/null || true)
if [ -z "${URL:-}" ] || [ "$URL" = "null" ]; then
  echo "Fill v3 had no build for $V, trying the old v2 API"
  B=$(curl -fsSL -H "User-Agent: $UA" "https://api.papermc.io/v2/projects/paper/versions/${V}/builds" | jq -r '.builds | last | .build')
  URL="https://api.papermc.io/v2/projects/paper/versions/${V}/builds/${B}/downloads/paper-${V}-${B}.jar"
fi
echo "Downloading $URL"
curl -fsSL -H "User-Agent: $UA" -o paper.jar "$URL"
ls -la paper.jar
