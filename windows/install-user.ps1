$ErrorActionPreference = "Continue"
$Root = "C:\Users\weber\AppData\Local\Wetterkurve"
$Log = Join-Path $Root "user-setup.log"
function Write-Log($message) {
    $line = "{0} {1}" -f (Get-Date -Format "s"), $message
    Add-Content -Path $Log -Value $line
    Write-Host $line
}

Write-Log "Wetterkurve user setup starting"
Get-Process Wetterkurve.Widget -ErrorAction SilentlyContinue | Stop-Process -Force
Get-AppxPackage *wean.de.Wetterkurve* -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Log "Removing widget package $($_.PackageFullName)"
    Remove-AppxPackage -Package $_.PackageFullName
}
New-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced" -Name "TaskbarDa" -PropertyType DWord -Value 0 -Force | Out-Null
Write-Log "Taskbar Widgets button disabled"

$exe = Join-Path $Root "app\Wetterkurve.exe"
if (Test-Path $exe) {
    if (-not (Get-Process Wetterkurve -ErrorAction SilentlyContinue)) {
        Start-Process $exe
        Write-Log "Started desktop app"
    } else {
        Write-Log "Desktop app already running"
    }
    Start-Sleep -Seconds 3
    Get-ChildItem "HKCU:\Control Panel\NotifyIconSettings" -ErrorAction SilentlyContinue | ForEach-Object {
        $path = (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).ExecutablePath
        if ($path -and $path -like "*Wetterkurve.exe") {
            New-ItemProperty -Path $_.PSPath -Name IsPromoted -PropertyType DWord -Value 1 -Force | Out-Null
            Write-Log "Promoted tray icon $($_.PSChildName)"
        }
    }
}

Write-Log "Tray app ready. Click the Wetterkurve icon near the clock."
