# Publishing Wetterkurve

## First release

1. Create a GNOME account at [extensions.gnome.org](https://extensions.gnome.org)
   and accept its terms when prompted by the upload command.
2. In the GitHub repository, create an environment named
   `gnome-extension-store` and require manual approval for deployments.
3. Add `EGO_USERNAME` and `EGO_PASSWORD` as **environment secrets**. Never add
   them to a repository secret, workflow file, shell history, or `.env` file.
4. Run `./scripts/release.sh` locally and inspect the generated ZIP.
5. Create and push a signed or annotated tag, for example `v1.0.0`. The
   **Verify Wetterkurve release** workflow repeats the GNOME checks. Build
   Windows with the .NET 10 SDK from the same commit using
   `./scripts/release.sh`; generated binaries stay outside Git. After
   reviewing all packages, create the release and attach the GNOME ZIP,
   `Wetterkurve-Windows-x64.zip`. Android APKs are not distributed through
   GitHub.
6. To update extensions.gnome.org, start **Submit Wetterkurve to GNOME
   Extensions** from the Actions tab, enter that tested tag, and approve the
   protected `gnome-extension-store` environment only after reviewing its test
   logs.

The store assigns its own extension revision. The Git tag is Wetterkurve's
human-facing release version; do not add a `version` field to `metadata.json`.

## Store listing

Use [store/STORE_LISTING.md](../store/STORE_LISTING.md) for the public text,
[store/PRIVACY.md](../store/PRIVACY.md) for the privacy URL/text, and
[store/REVIEW_NOTES.md](../store/REVIEW_NOTES.md) if a reviewer asks how the
extension works.
