# Runs every automated test in the project and opens the reports.
# Usage (from the repo root):   .\run-all-tests.ps1
# Add -SkipDevice to skip the emulator tests if no emulator is running.
param([switch]$SkipDevice)

$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
$results = @()

Write-Host "`n=== 1/3  Android unit tests (51) ===" -ForegroundColor Cyan
Push-Location "$root\android"
.\gradlew testDebugUnitTest --console=plain
$results += [pscustomobject]@{ Suite = "Android unit (51)"; Passed = ($LASTEXITCODE -eq 0) }

if (-not $SkipDevice) {
    Write-Host "`n=== 2/3  Android on-device + Compose UI tests (6) - emulator must be open ===" -ForegroundColor Cyan
    .\gradlew connectedDebugAndroidTest --console=plain
    $results += [pscustomobject]@{ Suite = "Android on-device (6)"; Passed = ($LASTEXITCODE -eq 0) }
}
Pop-Location

Write-Host "`n=== 3/3  Backend API tests (13) ===" -ForegroundColor Cyan
Push-Location "$root\backend"
if (-not (Test-Path node_modules)) { npm install --silent }
npm test
$results += [pscustomobject]@{ Suite = "Backend API (13)"; Passed = ($LASTEXITCODE -eq 0) }
Pop-Location

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
$results | ForEach-Object {
    $mark = if ($_.Passed) { "PASS" } else { "FAIL" }
    $color = if ($_.Passed) { "Green" } else { "Red" }
    Write-Host ("  {0,-26} {1}" -f $_.Suite, $mark) -ForegroundColor $color
}

# Open the HTML reports in the default browser.
$unit = "$root\android\app\build\reports\tests\testDebugUnitTest\index.html"
$device = "$root\android\app\build\reports\androidTests\connected\debug\index.html"
if (Test-Path $unit) { Start-Process $unit }
if (-not $SkipDevice -and (Test-Path $device)) { Start-Process $device }
