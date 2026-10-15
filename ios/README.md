# Wetterkurve for iPhone

Native SwiftUI app and WidgetKit extension for iOS 17 or newer. The app saves up to three places chosen through Open-Meteo geocoding, shows the current weather and an hourly chart from midnight today through 23:00 two days later, and offers separate cloud, wind, and feels-like switches. English is the initial language; German is saved as a user choice. There is no GPS, account, advertising, analytics, or payment code.

The small widget shows current conditions, the medium widget shows the next six hours, and the large widget shows the three-day chart. Each widget can select a saved place using its configuration; without a choice it follows the active app place. Settings and forecast cache use the shared App Group `group.de.wean.wetterkurve`. Forecasts refresh on opening the app or when WidgetKit grants a timeline refresh, with a 10-minute cache freshness threshold.

## Build and test

Install Xcode 16 or newer and XcodeGen, then run from `ios/`:

```sh
xcodegen generate
xcodebuild -project Wetterkurve.xcodeproj -scheme Wetterkurve \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO test
```

The UI test uses live Open-Meteo geocoding and forecast responses, so network access is needed. It checks saved German language, Berlin place search/selection, and a successful weather refresh. The unit tests validate saved-place limits and complete 72-hour data. `../test.sh` runs the existing GNOME verification.

For device builds, use Apple Team `F3273K985F` with Automatic Signing. The app ID is `de.wean.wetterkurve`, its WidgetKit extension is `de.wean.wetterkurve.widgets`, and both need the App Group capability. Xcode-generated projects, derived data, archives, profiles and signing material stay outside Git.

Weather and geocoding data come from [Open-Meteo](https://open-meteo.com/) under CC BY 4.0. The app links to the provider; the chart widget carries the provider name. The [privacy notice](../website/public/privacy.html) describes the local cache, App Group, backup behavior and direct API requests.
