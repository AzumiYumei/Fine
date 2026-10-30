param([switch]$Release)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$localJdk = Get-ChildItem "$PSScriptRoot/.tools/jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    throw '请安装 JDK 17 并设置 JAVA_HOME，或使用 Android Studio 的 Gradle JDK。'
}
$variant = if ($Release) { 'Release' } else { 'Preview' }
& ./gradlew.bat --no-daemon "-Dorg.gradle.java.home=$env:JAVA_HOME" "-Dorg.gradle.daemon.registry.base=$PSScriptRoot/.gradle/daemon" ":app:assemble$variant" :app:testDebugUnitTest :app:lintDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
