$ErrorActionPreference = "Stop"
$log = "$env:LOCALAPPDATA\Wetterkurve\msix-install.log"
$msix = Get-ChildItem "$env:LOCALAPPDATA\Wetterkurve\dist\windows" -Recurse -Filter "Wetterkurve.Widget_*_x64.msix" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
"$(Get-Date -Format s) installing $($msix.FullName)" | Set-Content $log
try {
    Add-AppxPackage -Path $msix.FullName -ForceUpdateFromAnyVersion -ForceApplicationShutdown
    "$(Get-Date -Format s) APPX_OK" | Add-Content $log
} catch {
    "$(Get-Date -Format s) APPX_FAIL $($_.Exception.Message)" | Add-Content $log
}
Get-AppxPackage | Where-Object { $_.Name -eq 'Wetterkurve.Widget' -or $_.Name -like '*.Wetterkurve' } | Format-List Name, Version, Status, PackageFullName | Out-String | Add-Content $log
