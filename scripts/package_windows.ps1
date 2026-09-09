$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    $java = Get-Command java -ErrorAction Stop
} else {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $root "desktop")

mvn clean package
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue target\jpackage-input
New-Item -ItemType Directory -Force target\jpackage-input | Out-Null
Copy-Item target\pulseos-1.0-SNAPSHOT.jar target\jpackage-input\
mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\jpackage-input
New-Item -ItemType Directory -Force target\jpackage-input\javafx | Out-Null
Get-ChildItem target\jpackage-input\javafx-*.jar | Move-Item -Destination target\jpackage-input\javafx
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue target\installer

jpackage `
  --type exe `
  --name PulseOS `
  --app-version 1.0.0 `
  --vendor PulseOS `
  --description "PulseOS device health and healing center" `
  --input target\jpackage-input `
  --main-jar pulseos-1.0-SNAPSHOT.jar `
  --main-class com.pulseos.Main `
  --java-options "--module-path `$APPDIR/javafx --add-modules javafx.controls,javafx.fxml" `
  --dest target\installer `
  --java-options "-Xmx1g"

Write-Host "Created Windows installer(s) in desktop\target\installer"
