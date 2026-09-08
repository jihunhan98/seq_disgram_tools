#!/usr/bin/env bash
# Serves the static frontend on port 5001 using nothing but the JDK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-5001}"

exec java "$ROOT/frontend/server/StaticServer.java" "$PORT" "$ROOT/frontend"
