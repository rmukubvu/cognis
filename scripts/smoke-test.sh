#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_CORE_TESTS="${COGNIS_SMOKE_RUN_CORE_TESTS:-1}"
BUILD_APP="${COGNIS_SMOKE_BUILD_APP:-1}"

echo "[smoke] root: $ROOT_DIR"

if [[ "$RUN_CORE_TESTS" == "1" ]]; then
  echo "[smoke] running targeted core tests"
  (
    cd "$ROOT_DIR"
    mvn -pl cognis-core -Dtest=SqliteConversationStoreTest test
  )
fi

if [[ "$BUILD_APP" == "1" ]]; then
  echo "[smoke] compiling app wiring"
  (
    cd "$ROOT_DIR"
    mvn -pl cognis-app -am -DskipTests compile
  )
fi

echo "[smoke] PASS"
