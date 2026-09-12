# Wetterkurve for Android

Homescreen widgets plus a configuration app: up to three saved Open-Meteo
locations. The app is settings only (large location buttons, search,
cloud and wind toggles, chart legend, refresh). The three-day chart lives
in the chart widget. The temperature
widget is a thin full-width bar with a refresh control.

English is the default. Select **Deutsch** in the app for German; both
widgets follow the saved choice.

## Build

Needs JDK 21 and an Android SDK (`compileSdk` 36).

```bash
source scripts/android-env.sh
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Install with
`adb install -r` or copy the file to the phone.

Add a widget: long-press the home screen → Widgets → Wetterkurve.

## Tests

`./gradlew :core:test` ports `tests/weather.test.js`: 72-hour midnight window,
three weekday segments, WMO labels, geocoding, location JSON, de/en.

GNOME `./test.sh` is unchanged and does not run the Android suite.
