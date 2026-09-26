#!/bin/sh
# Compile and start Pac-Man (macOS / Linux): ./run.sh
cd "$(dirname "$0")/Pacman/src" || exit 1

if ! command -v javac >/dev/null 2>&1; then
    echo "Java JDK was not found. Install it from https://adoptium.net and try again."
    exit 1
fi

echo "Compiling Pac-Man..."
javac *.java || { echo "Compiling failed - see the errors above."; exit 1; }
exec java App
