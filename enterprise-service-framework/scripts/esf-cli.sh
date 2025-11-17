#!/bin/bash

###############################################################################
# Enterprise Service Framework - CLI Wrapper Script
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Find the CLI JAR
CLI_JAR="$PROJECT_DIR/build/libs/enterprise-service-framework-1.0.0-SNAPSHOT-all.jar"

# If JAR doesn't exist, try to build it
if [ ! -f "$CLI_JAR" ]; then
    echo "CLI JAR not found. Building..."
    cd "$PROJECT_DIR"
    ./gradlew shadowJar
fi

# Run the CLI
java -jar "$CLI_JAR" "$@"

