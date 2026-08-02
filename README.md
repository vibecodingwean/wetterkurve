# München Wetter

Eine native GNOME-Shell-Erweiterung für eine kompakte Wetterübersicht in
München. Im Panel stehen Wetterzustand und Temperatur. Ein Klick öffnet eine
Drei-Tage-Ansicht von heute 00:00 Uhr bis übermorgen 23:00 Uhr mit geglätteter
Temperaturkurve, gefühlter Temperatur, Regenwahrscheinlichkeit und
Niederschlagsmenge. Eine farblich wechselnde Tagesleiste oberhalb der Kurven
ordnet jeden 24-Stunden-Abschnitt dem ausgeschriebenen Wochentag zu.

Die Daten kommen von [Open-Meteo](https://open-meteo.com/) und benötigen
keinen API-Schlüssel.

## Installation

```bash
./install.sh
```

Unter Wayland muss man sich nach der ersten Installation gegebenenfalls einmal
ab- und wieder anmelden. Danach kann die Erweiterung auch mit der App
„Erweiterungen“ ein- und ausgeschaltet werden.

## Bedienung

- Klick auf die Wetteranzeige: Detailansicht öffnen
- Kreispfeil: Prognose sofort aktualisieren
- Automatische Aktualisierung: alle 20 Minuten

Die Datenquelle und der Zeitpunkt der letzten Aktualisierung stehen unten im
Popup.

## Entwicklung

```bash
./test.sh
```

Getestet für GNOME Shell 50.
