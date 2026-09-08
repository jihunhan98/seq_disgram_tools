#!/usr/bin/env bash
# Starts the Spring Boot API on port 5000.
# Requires config/application-local.yml (see config/application-local.yml.example).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -f "$ROOT/config/application-local.yml" ]]; then
    echo "config/application-local.yml is missing."
    echo "Copy the template and fill in the real values:"
    echo "    cp config/application-local.yml.example config/application-local.yml"
    exit 1
fi

cd "$ROOT/backend"

if [[ -x ./mvnw ]]; then
    exec ./mvnw spring-boot:run
fi
exec mvn spring-boot:run
