#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../desktop"
mvn clean package
APP_NAME="PulseOS"
JAR=$(find target -maxdepth 1 -name "*.jar" ! -name "original-*.jar" | head -1)
if [ -z "${JAR}" ]; then echo "No packaged JAR found"; exit 1; fi
rm -rf target/PulseOS.app
JFX_MODULES="$(find "$HOME/.m2/repository/org/openjfx" -name 'javafx-*-21*.jar' | paste -sd, -)"
mvn javafx:jlink
echo "PulseOS build complete. Use the generated runtime/app on macOS."
