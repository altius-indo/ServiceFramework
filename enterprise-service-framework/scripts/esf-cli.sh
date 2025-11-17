#!/bin/bash

###############################################################################
# Enterprise Service Framework - CLI Wrapper Script
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Change to project directory
cd "$PROJECT_DIR"

# Use Gradle task to run CLI (recommended)
if [ -f "./gradlew" ]; then
    ./gradlew cli -PcliArgs="$*"
else
    echo "Error: Gradle wrapper not found"
    exit 1
fi

