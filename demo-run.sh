#!/usr/bin/env bash
set -e

PORT=${1:-8080}

echo "→ Building..."
mvn install -DskipTests -q

MAIN=cognis-app/target/cognis-app-0.1.0-SNAPSHOT.jar
VERTICAL=cognis-vertical-humanitarian/target/cognis-vertical-humanitarian-0.1.0-SNAPSHOT.jar
DEPS=$(mvn -pl cognis-app dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null)

echo "→ Starting gateway on port $PORT (with humanitarian vertical)..."
exec java -cp "$MAIN:$VERTICAL:$DEPS" \
     io.cognis.app.CognisApplication gateway --port "$PORT"
