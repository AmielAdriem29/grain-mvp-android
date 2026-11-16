<#
.SYNOPSIS
  Runs the Android integration or performance tests against a local test backend.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite integration
  powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite performance

  Needs: .NET 10 SDK, Docker running, Android SDK (adb), one emulator or phone
  connected, and the ImmatureBackend repo next to this one. See testing/README.md.
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet('integration', 'performance')][string]$Suite,
    [int]$Port = 8000,
    [string]$BackendRoot = (Join-Path $PSScriptRoot '..\..\ImmatureBackend')
)

$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..')
$build = Join-Path $PSScriptRoot 'build'
$results = Join-Path $build 'results'
$session = Join-Path $build 'local-api.json'
$hostProject = Join-Path $PSScriptRoot 'local-api-host\LocalApiHost.csproj'
New-Item -ItemType Directory -Force $results | Out-Null
if (Test-Path $session) { Remove-Item $session }

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($sdk -and (Test-Path "$sdk\platform-tools\adb.exe")) { return "$sdk\platform-tools\adb.exe" }
    }
    throw 'adb not found. Install the Android SDK or set ANDROID_HOME.'
}

# ---- Checks -------------------------------------------------------------
if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) { throw '.NET 10 SDK not found (dotnet).' }
docker info *> $null; if ($LASTEXITCODE -ne 0) { throw 'Docker is not running.' }
if (-not (Test-Path (Join-Path $BackendRoot 'ImmatureBackend.Api'))) { throw "Backend repo not found at $BackendRoot. Pass -BackendRoot." }
$adb = Find-Adb
$devices = & $adb devices | Select-String "`tdevice$"
if ($devices.Count -ne 1) { throw "Connect exactly one emulator or phone (found $($devices.Count))." }

# Forward slashes: a trailing '\' before a closing quote would escape the quote on the dotnet command line.
$backendRootFull = (Resolve-Path $BackendRoot).Path.TrimEnd('\').Replace('\', '/') + '/'
$androidCommit = (git -C $repo rev-parse --short HEAD)
$backendCommit = (git -C $backendRootFull rev-parse --short HEAD)
$backendBranch = (git -C $backendRootFull rev-parse --abbrev-ref HEAD)
$device = (& $adb shell getprop ro.product.model).Trim() + ', Android ' + (& $adb shell getprop ro.build.version.release).Trim()

# ---- Start the local backend --------------------------------------------
Write-Host "Building local test backend (backend $backendBranch@$backendCommit)..."
dotnet build $hostProject -c Release "-p:BackendRoot=$backendRootFull" -o (Join-Path $build 'host') | Out-File (Join-Path $results 'host-build.log')
if ($LASTEXITCODE -ne 0) { throw "Host build failed, see $results\host-build.log" }

$hostProc = $null
$exitCode = 1
try {
    $hostProc = Start-Process dotnet -PassThru -NoNewWindow `
        -ArgumentList @((Join-Path $build 'host\LocalApiHost.dll'), '--port', $Port, '--out', $session) `
        -RedirectStandardOutput (Join-Path $results "host-$Suite.log") `
        -RedirectStandardError (Join-Path $results "host-$Suite.err.log")

    Write-Host 'Waiting for the local backend (first run pulls postgres:17-alpine)...'
    $deadline = (Get-Date).AddMinutes(5)
    while (-not (Test-Path $session)) {
        if ($hostProc.HasExited) { throw "Local backend exited, see $results\host-$Suite.err.log" }
        if ((Get-Date) -gt $deadline) { throw 'Local backend did not start within 5 minutes.' }
        Start-Sleep -Seconds 2
    }
    $cfg = Get-Content $session -Raw | ConvertFrom-Json

    & $adb reverse "tcp:$Port" "tcp:$Port" | Out-Null
    & $adb logcat -c

    $package = if ($Suite -eq 'integration') { 'com.grainmvp.android.integration' } else { 'com.grainmvp.android.performance' }
    $started = Get-Date
    Push-Location $repo
    try {
        & .\gradlew.bat connectedDebugAndroidTest `
            "-Pandroid.testInstrumentationRunnerArguments.package=$package" `
            "-Pandroid.testInstrumentationRunnerArguments.baseUrl=$($cfg.baseUrl)" `
            "-Pandroid.testInstrumentationRunnerArguments.captureToken=$($cfg.captureToken)" `
            "-Pandroid.testInstrumentationRunnerArguments.dashboardToken=$($cfg.dashboardToken)"
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }

    $log = Join-Path $results "$Suite-logcat.txt"
    & $adb logcat -d -v time -s GrainTest:I GrainPerf:I GrainPerf:W TestRunner:I | Out-File $log -Encoding utf8

    $summary = Join-Path $results "$Suite-summary.md"
    @"
# Android $Suite tests

- Run: $($started.ToString('yyyy-MM-dd HH:mm')) ($([int]((Get-Date) - $started).TotalSeconds) s)
- Build: android@$androidCommit, backend $backendBranch@$backendCommit
- Environment: local test backend ($($cfg.baseUrl), real API + PostgreSQL in Docker, local image store instead of Cloudinary) + $device
- Command: powershell -ExecutionPolicy Bypass -File testing/run-android-tests.ps1 -Suite $Suite
- Gradle result: $(if ($exitCode -eq 0) { 'all tests passed' } else { "failures (exit $exitCode)" })
- JUnit report: app/build/reports/androidTests/connected/debug/index.html
- Evidence log: testing/build/results/$Suite-logcat.txt

## Evidence

``````
$((Get-Content $log | Select-String 'GrainTest|GrainPerf') -join "`n")
``````
"@ | Out-File $summary -Encoding utf8
    Write-Host "Summary: $summary"
}
finally {
    & $adb reverse --remove "tcp:$Port" 2>$null | Out-Null
    if ($hostProc -and -not $hostProc.HasExited) { Stop-Process -Id $hostProc.Id -Force }
    if (Test-Path $session) { Remove-Item $session }
}
exit $exitCode
