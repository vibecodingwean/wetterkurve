$ErrorActionPreference = "Continue"
$log = "C:\Users\weber\AppData\Local\Wetterkurve\uninstall-widget.log"
function Write-Log($message) {
    $line = "{0} {1}" -f (Get-Date -Format "s"), $message
    Add-Content -Path $log -Value $line
}
Get-Process Wetterkurve.Widget -ErrorAction SilentlyContinue | Stop-Process -Force
Get-AppxPackage *wean.de.Wetterkurve* | ForEach-Object {
    Write-Log "Removing $($_.PackageFullName)"
    Remove-AppxPackage -Package $_.PackageFullName
}
New-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced" -Name "TaskbarDa" -PropertyType DWord -Value 0 -Force | Out-Null
Write-Log "TaskbarDa=0"
Write-Log "done"
Get-AppxPackage *wean.de.Wetterkurve* | Format-List Name, Version | Out-String | Add-Content $log
