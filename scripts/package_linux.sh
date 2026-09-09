#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/desktop"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "JDK 21 is required: jpackage was not found." >&2
  exit 1
fi

mvn clean package
rm -rf target/jpackage-input target/installer
mkdir -p target/jpackage-input
cp target/pulseos-1.0-SNAPSHOT.jar target/jpackage-input/
mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/jpackage-input
mkdir -p target/jpackage-input/javafx
find target/jpackage-input -maxdepth 1 -name 'javafx-*.jar' -exec mv {} target/jpackage-input/javafx/ \;

jpackage \
  --type deb \
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

echo "Created Linux package(s) in $ROOT/desktop/target/installer"
