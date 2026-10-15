<#
Run layout regressions against the actual offline RN/Fabric renderer.
Personal-device interaction requires -AllowPersonalDevice (user authorization).
Protects data with install -r; refuses to restart an existing focus session.
No device color-mode, resolution, density, database or preference changes.
Use -SkipAppInstall -Method nativeScrollViewCoordinatesCoincide to measure the
currently installed app before installing the fix. Reports stay under build/.
#>
[CmdletBinding()]
param(
    [switch]$AllowPersonalDevice,
    [switch]$SkipAppInstall,
    [string]$Method,
    [string]$DeviceSerial,
    [switch]$Screenshots
)
$ErrorActionPreference = 'Stop'
if (-not $AllowPersonalDevice) { throw 'User authorization is required: -AllowPersonalDevice.' }
$repo = Split-Path $PSScriptRoot -Parent
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) {
    $sdkLine = Get-Content (Join-Path $repo 'local.properties') | Where-Object { $_ -match '^sdk.dir=' }
    $sdk = ($sdkLine -replace '^sdk.dir=', '' -replace '\\:', ':' -replace '\\\\', '\')
}
$adb = if ($env:ADB) { $env:ADB } else { Join-Path $sdk 'platform-tools/adb.exe' }
$run = Join-Path $repo ("build/layout-regression/" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $run -Force | Out-Null
function Invoke-Adb {
    $output = & $adb -s $DeviceSerial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $output" }
    return $output
}
& $adb start-server
if ($LASTEXITCODE) { throw 'ADB server failed.' }
if (-not $DeviceSerial) {
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        $devices = @(& $adb devices | Where-Object { $_ -match '^((?!emulator-).+)\s+device$' } |
            ForEach-Object { ($_ -split '\s+')[0] })
        if ($devices.Count -gt 1) { throw 'Specify -DeviceSerial when multiple devices are connected.' }
        if ($devices.Count -eq 1) { $DeviceSerial = $devices[0]; break }
        Start-Sleep -Seconds 2
    }
}
if (-not $DeviceSerial) { throw 'No device found. Check wireless debugging and local-network proxy routing.' }
# Read only the existence of the dedicated timer snapshot, never credentials.
Invoke-Adb shell run-as com.example.yanji pwd | Out-Null
& $adb -s $DeviceSerial shell run-as com.example.yanji test -f no_backup/active_timer_session.json
if ($LASTEXITCODE -eq 0) { throw 'An existing focus snapshot is present; refusing to restart the user session.' }
if (-not $SkipAppInstall) {
    Invoke-Adb install -r -d (Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk')
}
Invoke-Adb install -r (Join-Path $repo 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
$testClass = 'com.example.yanji.layout.TabLayoutInstrumentedTest'
if ($Method) { $testClass += "#$Method" }
$runnerArgs = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $testClass,
    '-e', 'allowPersonalDevice', 'true')
if ($Screenshots) { $runnerArgs += @('-e', 'screenshots', 'true') }
$runnerArgs += 'com.example.yanji.test/androidx.test.runner.AndroidJUnitRunner'
# Only the screenshot names this harness owns; a stale failure.png from an
# earlier run must never be mistaken for the current one.
$ownedScreenshots = @('today', 'focus', 'review', 'failure')
foreach ($name in $ownedScreenshots) {
    Invoke-Adb shell run-as com.example.yanji rm -f "cache/layout-regression/$name.png" | Out-Null
}
$result = Invoke-Adb @runnerArgs
$result | Set-Content (Join-Path $run 'instrumentation.log')
$result | Where-Object { $_ -match 'INSTRUMENTATION_STATUS: (test=|stack=)|Tests run:|OK \(|FAILURES|Time:' }
Invoke-Adb logcat -d -s YanjiLayoutTest:I '*:S' | Set-Content (Join-Path $run 'coordinates.log')
$testsPassed = ($result -join "`n") -match 'OK \(\d+ tests?\)'
function Export-DeviceScreenshot([string]$name) {
    # Binary stream copying avoids PowerShell text encoding corrupting PNGs.
    $startInfo = [Diagnostics.ProcessStartInfo]::new($adb)
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    foreach ($argument in @('-s', $DeviceSerial, 'exec-out', 'run-as', 'com.example.yanji',
        'cat', "cache/layout-regression/$name.png")) { $startInfo.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::Start($startInfo)
    $file = [IO.File]::Create((Join-Path $run "$name.png"))
    try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
    $process.WaitForExit()
    if ($process.ExitCode) { throw "Screenshot export failed: $name" }
    $pngBytes = [IO.File]::ReadAllBytes((Join-Path $run "$name.png"))
    if ($pngBytes.Length -lt 8 -or [BitConverter]::ToString($pngBytes, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') {
        throw "Screenshot is not a valid PNG: $name"
    }
}
# A missing failure.png must never hide the original failure: it is written only
# by await/awaitRoom timeouts (TabLayoutInstrumentedTest.captureFailure), so a
# plain assertion failure or a crashed runner legitimately leaves no image.
# Diagnostics are therefore best-effort and presence-checked.
function Export-FailureEvidence {
    try {
        & $adb -s $DeviceSerial shell run-as com.example.yanji test -f "cache/layout-regression/failure.png" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "No failure.png on device: the failing test never captured one. The instrumentation log is the evidence."
            return
        }
        Export-DeviceScreenshot 'failure'
    } catch {
        Write-Warning "Failure diagnostics unavailable: $_"
    }
}
if ($Screenshots -and $testsPassed) {
    foreach ($tab in @('today', 'focus', 'review')) { Export-DeviceScreenshot $tab }
}
$failure = if ($testsPassed) { $null } else { "Layout regression failed. Reports: $run" }
try {
    # A failed run is exactly when the diagnostics matter most.
    if (-not $testsPassed) { Export-FailureEvidence }
} finally {
    # Cleanup must survive a diagnostic failure: relaunch the app, drop this
    # run's screenshots, and uninstall the test package either way.
    Invoke-Adb shell am start -n com.example.yanji/.MainActivity
    foreach ($name in $ownedScreenshots) {
        Invoke-Adb shell run-as com.example.yanji rm -f "cache/layout-regression/$name.png" | Out-Null
    }
    Invoke-Adb uninstall com.example.yanji.test
}
if ($failure) { throw $failure }
Write-Host "Layout regression passed. Reports: $run"
