# Battery Widget releases

The release target is the `:app` / `release` variant in this repository. Validate its
identity against the final bundle and Google Play before each upload.

The Gradle release bundle is unsigned. Sign a separate copy with the existing upload
keystore, using `jarsigner` password environment arguments. Never store credentials or
the keystore here. `play/release-config.json` records environment variable names only.

Keep signed bundles, build logs, inventories, source patches, and release-state records
under the ignored `play/artifacts/<versionName>-<versionCode>/` directory. These records
describe a particular release and do not authorize subsequent remote changes.

Local release notes cover English and Simplified Chinese. Existing Play listings and
screenshots are not backed up here and should not be replaced by an empty metadata sync.

This machine uses the `gplay` profile `battery-widget`, authenticated as
`play-publisher@androidbatteryindicator.iam.gserviceaccount.com`. The Publisher API is
enabled in the existing `androidbatteryindicator` Cloud project. The JSON key is stored
outside the repository at
`~/.gplay/credentials/androidbatteryindicator-play-publisher.json` with mode `0600`.
Do not copy its contents into release records, logs, or version control.

With the owner's explicit approval, Play access covers all existing and future apps
in developer account `5332741159735070027`: read app information and bulk reports,
publish production releases, and publish testing releases, including the dependent
permissions required by Play Console. Financial, order, and user-administration
permissions are not granted. The same profile can be used with each app's package
name; credential access does not authorize publishing unrelated apps.

Authentication was verified on 2026-09-08 by reading Battery Widget's production
release `31 (3.1)` through `gplay tracks releases list`. After saving account-level
permissions, cross-app access was verified by reading Simple QR & Barcode Scanner
(`com.github.xckevin927.android.simple.scanner`), production release `150 (1.5.0)`.

The SDK 36 monitoring implementation was checked before the version bump; see
`docs/widget-monitoring-validation.md`. Each release still requires final artifact,
signature, build, version, and Play Console verification.
