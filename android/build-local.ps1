# Local build helper. Sets up JDK 17, Gradle 8.11.1 and the SDK path, then
# runs unit tests and assembles the debug APK. Log lands in build-local.log.
$ErrorActionPreference = "Continue"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:LOCALAPPDATA\gradle-8.11.1\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
Set-Location $PSScriptRoot
[string[]]$tasks = if ($args.Count -gt 0) { @($args) } else { @("testDebugUnitTest", "assembleDebug") }
$gradle = "$env:LOCALAPPDATA\gradle-8.11.1\bin\gradle.bat"
if (-not (Test-Path $gradle)) { Write-Host "gradle not found at $gradle"; exit 1 }
& $gradle @tasks --console=plain --stacktrace 2>&1 | Tee-Object -FilePath "$PSScriptRoot\build-local.log"
Write-Host "exit code: $LASTEXITCODE"
