$ErrorActionPreference = "Stop"
$log = "C:\Users\weber\AppData\Local\Wetterkurve\msix-install.log"
$msix = Get-ChildItem "C:\Users\weber\AppData\Local\Wetterkurve\dist\windows" -Recurse -Filter "Wetterkurve.Widget_*_x64.msix" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
"$(Get-Date -Format s) installing $($msix.FullName)" | Set-Content $log
try {
    Add-AppxPackage -Path $msix.FullName -ForceUpdateFromAnyVersion -ForceApplicationShutdown
    "$(Get-Date -Format s) APPX_OK" | Add-Content $log
} catch {
    "$(Get-Date -Format s) APPX_FAIL $($_.Exception.Message)" | Add-Content $log
}
Get-AppxPackage *wean* | Format-List Name, Version, Status, PackageFullName | Out-String | Add-Content $log
