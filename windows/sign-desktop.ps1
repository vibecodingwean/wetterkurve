$ErrorActionPreference = "Stop"
$app = Join-Path $env:LOCALAPPDATA "Wetterkurve\app"
$pfx = Join-Path $env:LOCALAPPDATA "Wetterkurve\certs\wetterkurve.pfx"
$signtool = "$env:USERPROFILE\.nuget\packages\microsoft.windows.sdk.buildtools\10.0.26100.6584\bin\10.0.26100.0\x64\signtool.exe"
if (-not $env:WETTERKURVE_PFX_PASSWORD) {
    throw "Set WETTERKURVE_PFX_PASSWORD before signing."
}
Get-ChildItem $app -Recurse | Unblock-File
Get-ChildItem $app -Include *.exe,*.dll -Recurse | ForEach-Object {
    & $signtool sign /fd SHA256 /f $pfx /p $env:WETTERKURVE_PFX_PASSWORD /td SHA256 $_.FullName | Out-Null
    Write-Host "signed $($_.Name)"
}
