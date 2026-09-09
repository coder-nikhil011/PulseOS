#!/bin/bash
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/desktop"

mvn clean package
rm -rf target/jpackage-input
mkdir -p target/jpackage-input
cp target/pulseos-1.0-SNAPSHOT.jar target/jpackage-input/
mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/jpackage-input
mkdir -p target/jpackage-input/javafx
find target/jpackage-input -maxdepth 1 -name 'javafx-*.jar' -exec mv {} target/jpackage-input/javafx/ \;
rm -rf target/installer

jpackage \
  --type dmg \
  --name PulseOS \
  --app-version 1.0.0 \
  --vendor PulseOS \
  --description "PulseOS device health and healing center" \
  --input target/jpackage-input \
  --main-jar pulseos-1.0-SNAPSHOT.jar \
  --main-class com.pulseos.Main \
  --java-options "--module-path \$APPDIR/javafx --add-modules javafx.controls,javafx.fxml" \
  --dest target/installer \
  --java-options "-Xmx1g"

echo "Created macOS installer(s) in $ROOT/desktop/target/installer"
