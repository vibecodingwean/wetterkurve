$ErrorActionPreference = "Continue"
$path = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced"
New-ItemProperty -Path $path -Name "TaskbarDa" -PropertyType DWord -Value 0 -Force | Out-Null
Set-ItemProperty -Path $path -Name "TaskbarDa" -Value 0
"TaskbarDa=$((Get-ItemProperty $path).TaskbarDa)" | Set-Content "C:\Users\weber\AppData\Local\Wetterkurve\taskbar-widgets.log"
Stop-Process -Name explorer -Force
Start-Sleep 2
if (-not (Get-Process explorer -ErrorAction SilentlyContinue)) {
    Start-Process "$env:WINDIR\explorer.exe"
}
