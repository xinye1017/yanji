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
$result = Invoke-Adb @runnerArgs
$result | Set-Content (Join-Path $run 'instrumentation.log')
$result | Where-Object { $_ -match 'INSTRUMENTATION_STATUS: (test=|stack=)|Tests run:|OK \(|FAILURES|Time:' }
Invoke-Adb logcat -d -s YanjiLayoutTest:I '*:S' | Set-Content (Join-Path $run 'coordinates.log')
if ($Screenshots) {
    foreach ($tab in @('today', 'focus', 'review')) {
        # Binary stream copying avoids PowerShell text encoding corrupting PNGs.
        $startInfo = [Diagnostics.ProcessStartInfo]::new($adb)
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardOutput = $true
        foreach ($argument in @('-s', $DeviceSerial, 'exec-out', 'run-as', 'com.example.yanji',
            'cat', "cache/layout-regression/$tab.png")) { $startInfo.ArgumentList.Add($argument) }
        $process = [Diagnostics.Process]::Start($startInfo)
        $file = [IO.File]::Create((Join-Path $run "$tab.png"))
        try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
        $process.WaitForExit()
        if ($process.ExitCode) { throw "Screenshot export failed: $tab" }
        $pngBytes = [IO.File]::ReadAllBytes((Join-Path $run "$tab.png"))
        if ($pngBytes.Length -lt 8 -or [BitConverter]::ToString($pngBytes, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') {
            throw "Screenshot is not a valid PNG: $tab"
        }
    }
}
Invoke-Adb shell am start -n com.example.yanji/.MainActivity
Invoke-Adb shell run-as com.example.yanji rm -f cache/layout-regression/today.png `
    cache/layout-regression/focus.png cache/layout-regression/review.png
Invoke-Adb uninstall com.example.yanji.test
if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw "Layout regression failed. Reports: $run" }
Write-Host "Layout regression passed. Reports: $run"
