param(
    [switch]$SkipWidget = $true,
    [switch]$LaunchDesktop
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Dotnet = "C:\Program Files\dotnet\dotnet.exe"
$Configuration = "Release"
$Runtime = "win-x64"
$CertDir = Join-Path $env:LOCALAPPDATA "Wetterkurve\certs"
$DesktopOut = Join-Path $env:LOCALAPPDATA "Wetterkurve\app"
$MsixOut = Join-Path $Root "..\dist\windows"

function Invoke-Dotnet {
    param([string[]]$Arguments)
    & $Dotnet @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "dotnet $($Arguments -join ' ') failed with $LASTEXITCODE"
    }
}

Write-Host "Testing Wetterkurve.Core..."
Invoke-Dotnet @("test", (Join-Path $Root "Wetterkurve.Core.Tests\Wetterkurve.Core.Tests.csproj"), "-c", $Configuration, "--nologo")

Write-Host "Publishing desktop app to $DesktopOut..."
New-Item -ItemType Directory -Force -Path $DesktopOut | Out-Null
Invoke-Dotnet @(
    "publish",
    (Join-Path $Root "Wetterkurve.Desktop\Wetterkurve.Desktop.csproj"),
    "-c", $Configuration,
    "-r", $Runtime,
    "--self-contained", "false",
    "-o", $DesktopOut
)

if (-not $SkipWidget) {
    Write-Host "Building Windows widget package..."
    Invoke-Dotnet @(
        "publish",
        (Join-Path $Root "Wetterkurve.Widget\Wetterkurve.Widget.csproj"),
        "-c", $Configuration,
        "-r", $Runtime,
        "-p:Platform=x64",
        "-p:GenerateAppxPackageOnBuild=true"
    )

    $msix = Get-ChildItem -Path (Join-Path $Root "Wetterkurve.Widget") -Recurse -Filter "*.msix" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $msix) {
        $msix = Get-ChildItem -Path $MsixOut -Recurse -Filter "*.msix" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
    }
    if ($msix) {
        Write-Host "Found package $($msix.FullName)"
        try {
            Add-AppxPackage -Path $msix.FullName -ForceApplicationShutdown
            Write-Host "Widget package installed. Open Win+W and add Wetterkurve."
        } catch {
            Write-Warning "Sideload failed: $($_.Exception.Message)"
            Write-Warning "Enable Developer Mode or sideloading, then install: $($msix.FullName)"
        }
    } else {
        Write-Warning "No MSIX was produced. Desktop app is still available."
    }
}

if ($LaunchDesktop) {
    Start-Process (Join-Path $DesktopOut "Wetterkurve.exe")
}

Write-Host "Desktop app: $(Join-Path $DesktopOut 'Wetterkurve.exe')"
Write-Host "Widget: Win+W -> Widgets hinzufuegen -> Wetterkurve"
