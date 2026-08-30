# Wetterkurve

Wetterkurve is a native GNOME Shell extension that keeps a compact weather
overview for up to three saved locations in the top panel. Its popup shows a
three-day chart from today at midnight to 23:00 two days later, including
temperature, feels-like temperature, precipitation probability, precipitation,
and an alternating weekday strip.

Forecasts and location search come directly from the [Open-Meteo API](https://open-meteo.com/).
No API key is required.

![Wetterkurve showing its three-day weather chart](assets/wetterkurve-overview.png)

## Features

- Current conditions and temperature in the top panel
- Save and switch between up to three locations
- Live location search
- Temperature and feels-like curves plus precipitation information
- Automatic refresh every 20 minutes and a manual refresh button
- German on German-language systems; English on all other systems

## Install

1. Download `wetterkurve@wean.de.shell-extension.zip` from the
   [latest GitHub release](https://github.com/vibecodingwean/wetterkurve/releases/latest).
2. Install it from a terminal:

   ```bash
   gnome-extensions install --force ~/Downloads/wetterkurve@wean.de.shell-extension.zip
   ```

3. Enable **Wetterkurve** in the GNOME **Extensions** app.

On Wayland, log out and back in once if GNOME Shell does not discover the
extension immediately.

## Windows 11

The same Open-Meteo forecast runs on Windows 11 as a tray app, analogous to the
GNOME panel indicator: the current temperature stays near the clock, and a
click opens the three-day chart. Windows Widgets cannot replace that taskbar
weather button, so Wetterkurve is not installed into the Widgets Board.

On a Windows 11 machine with the .NET 10 SDK:

```powershell
.\windows\install.ps1 -LaunchDesktop
```

This publishes `Wetterkurve.exe` to `%LOCALAPPDATA%\Wetterkurve\app`. Closing
the window hides it in the notification area; a left-click restores it.

A prebuilt x64 build is in [`windows/release/Wetterkurve/Wetterkurve.exe`](windows/release/Wetterkurve/Wetterkurve.exe).

## Android

The same Open-Meteo forecast runs on Android as homescreen widgets. The app
stores up to three locations. A thin full-width widget shows the current
temperature; a second widget shows the three-day chart.

On a machine with JDK 17 or 21 and an Android SDK:

```bash
cd android
./gradlew :core:test :app:assembleDebug
```

Install `android/app/build/outputs/apk/debug/app-debug.apk`, then add a
**Wetterkurve** widget from the launcher widget picker. Details are in
[`android/README.md`](android/README.md).

`windows/deploy-bequiet.sh` syncs the Windows project to `bequiet` and runs the
install script there.

### Development install

```bash
./install.sh
```

This creates a development symlink instead of installing a release ZIP.

## Develop and package

```bash
./test.sh
```

This compiles the local GSettings schema, runs the tests, and creates
`dist/wetterkurve@wean.de.shell-extension.zip`.

## Release

1. Tag a tested commit as `vX.Y.Z` and push the tag.
2. GitHub Actions runs the tests, then creates a GitHub release with the
   installable ZIP attached.
3. Submit a tested tag to extensions.gnome.org manually through the protected
   **Submit Wetterkurve to GNOME Extensions** workflow.

Configure the `EGO_USERNAME` and `EGO_PASSWORD` secrets only in that protected
GitHub environment. Details and the first-release checklist are in
[docs/PUBLISHING.md](docs/PUBLISHING.md).

## License

MIT. See [LICENSE](LICENSE).
