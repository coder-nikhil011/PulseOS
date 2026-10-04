#!/bin/bash
set -euo pipefail
JAVA_HOME=$(/usr/libexec/java_home -v 21)
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
mvn -f "$SCRIPT_DIR/../desktop/pom.xml" clean javafx:run
