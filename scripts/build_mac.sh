#!/bin/bash
set -euo pipefail
JAVA_HOME=$(/usr/libexec/java_home -v 21)
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/../desktop"
mvn clean package
APP_NAME="PulseOS"
JAR=$(find target -maxdepth 1 -name "*.jar" ! -name "original-*.jar" | head -1)
if [ -z "${JAR}" ]; then echo "No packaged JAR found"; exit 1; fi
rm -rf target/PulseOS.app
JFX_MODULES="$(find "$HOME/.m2/repository/org/openjfx" -name 'javafx-*-21*.jar' | paste -sd, -)"
mvn javafx:jlink
echo "PulseOS build complete. Use the generated runtime/app on macOS."
