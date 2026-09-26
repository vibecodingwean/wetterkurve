# Publishing Wetterkurve

## First release

1. Create a GNOME account at [extensions.gnome.org](https://extensions.gnome.org).
2. Run `./scripts/release.sh` locally and inspect the generated ZIP.
3. Create and push a signed or annotated tag, for example `v1.0.0`. The
   **Verify Wetterkurve release** workflow repeats the GNOME checks. Build
   Windows with the .NET 10 SDK from the same commit using
   `./scripts/release.sh`; generated binaries stay outside Git. After
   reviewing all packages, create the release and attach the GNOME ZIP,
   `Wetterkurve-Windows-x64.zip`. Android APKs are not distributed through
   GitHub.
4. Upload the reviewed GNOME ZIP with `gnome-extensions upload`, supplying the
   password through standard input with `--password-file=-` (or through an
   already-open file descriptor). For example, with a secret manager command
   that writes only the password to standard output:

   ```sh
   secret-manager-command | gnome-extensions upload \
     --user "$GNOME_USERNAME" --password-file=- --accept-tos \
     dist/wetterkurve@wean.de.shell-extension.zip
   ```

   Read the [GNOME upload terms](https://extensions.gnome.org/upload/) before
   using `--accept-tos`. Do not pass the password with `--password` or save it
   in the repository. If the CLI is unavailable, sign in to the upload page
   and submit the same reviewed ZIP in the browser. GNOME reviews the submitted
   extension revision.

The optional **Submit Wetterkurve to GNOME Extensions** workflow can upload a
tested tag instead. It requires authentication secrets in the protected
`gnome-extension-store` environment and manual approval after reviewing its
test logs; without those secrets, the workflow cannot submit the ZIP.

The store assigns its own extension revision. The Git tag is Wetterkurve's
human-facing release version; do not add a `version` field to `metadata.json`.

## Store listing

Use [store/STORE_LISTING.md](../store/STORE_LISTING.md) for the public text,
[store/PRIVACY.md](../store/PRIVACY.md) for the privacy URL/text, and
[store/REVIEW_NOTES.md](../store/REVIEW_NOTES.md) if a reviewer asks how the
extension works.
