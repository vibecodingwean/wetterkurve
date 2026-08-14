# Reviewer notes

Wetterkurve is a GNOME Shell 50 extension written entirely in GJS.

- It starts no subprocesses and ships no executable binaries or third-party
  JavaScript dependencies.
- On enable it creates one panel indicator, starts refresh timers, and creates
  cancellable HTTPS requests to Open-Meteo.
- On disable it removes timers, cancels requests, and destroys the indicator.
- It uses GSettings only to persist up to three user-selected locations and the
  active location index.
- It does not access the clipboard, filesystem outside its own extension
  directory, shell commands, accounts, or any third-party service other than
  Open-Meteo.
