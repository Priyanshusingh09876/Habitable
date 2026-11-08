# Captures the backup evidence Ron's checklist asks for, straight from the emulator.
# Run from the repo root with the emulator open and the app on the Home screen:
#     .\docs\capture-backup.ps1
# It saves screenshots to docs\screenshots\ and then records a demo video.
$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb - open Android Studio > SDK Manager and note the SDK path" }
$out = Join-Path $PSScriptRoot "screenshots"
New-Item -ItemType Directory -Force $out | Out-Null

function Shot($name) {
    Read-Host "Put the emulator on: $name   then press Enter"
    & $adb exec-out screencap -p > (Join-Path $out "$name.png")
    Write-Host "  saved $name.png" -ForegroundColor Green
}

Shot "01_setup"
Shot "02_home_before"
Shot "03_sensors_live"
Shot "04_sensors_cold_night_chart"
Shot "05_home_red_below_limit"
Shot "06_issue_detail_tracker"
Shot "07_send_notice_email"
Shot "08_timeline_after_inspection"
Shot "09_sensors_offline_cache"
Shot "10_more_synced"

Write-Host "`nNow the backup video (max 3 minutes). Do the whole demo, then press Enter here to stop." -ForegroundColor Cyan
Read-Host "Press Enter to START recording"
$rec = Start-Process -FilePath $adb -ArgumentList "shell screenrecord --time-limit 180 --bit-rate 6000000 /sdcard/demo_backup.mp4" -PassThru -NoNewWindow
Read-Host "Recording... press Enter to STOP"
& $adb shell pkill -INT screenrecord
Start-Sleep 3
& $adb pull /sdcard/demo_backup.mp4 (Join-Path $PSScriptRoot "demo_backup.mp4")
Write-Host "Saved docs\demo_backup.mp4 - if it is over 100 MB keep it out of git (it is listed in .gitignore) and play it from your laptop." -ForegroundColor Green
