# Slot Tracker

An offline Android app whose main surface is a home-screen widget showing today's time slots and the
tasks scheduled in them, with checkboxes you tick right on the widget. Built for a Samsung Galaxy S24.

- Spec: [SPEC.md](SPEC.md) (source of truth)
- Decisions where the spec was silent: [docs/DECISIONS.md](docs/DECISIONS.md)

## Install on the phone

1. Open the repo's **Releases** page in the phone's browser and download the latest `slot-tracker-x.y.z.apk`.
2. Open the download. The first time, Android asks to allow **Install unknown apps** for the browser; allow it.
3. Later releases install over the top and keep your data, as long as they are signed with the same key (below).

Every push to `main` also builds an APK, available as an artifact on the workflow run.

## Release signing (one-time setup)

CI signs the release APK with your keystore. **Android only installs an update in place if it is signed
with the same key as the installed app**, so create the keystore once and keep a backup of it and its password.
If you lose it, the only way to install a newer build is to uninstall the app, which deletes its data
(export a backup first).

1. Generate the keystore (needs a JDK; `keytool` ships with it). Use the same password for the store and the key:

   ```sh
   keytool -genkeypair -v -keystore slot-tracker-release.jks -alias slottracker \
     -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Slot Tracker"
   ```

   Store `slot-tracker-release.jks` somewhere safe, **outside this repo** (the `.gitignore` excludes `*.jks` as a safety net).

2. Add four repository secrets under **Settings → Secrets and variables → Actions → New repository secret**:

   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | The keystore file, base64-encoded (see below) |
   | `KEYSTORE_PASSWORD` | The password you chose |
   | `KEY_ALIAS` | `slottracker` (or the alias you used) |
   | `KEY_PASSWORD` | The same password |

   To base64-encode the keystore onto the clipboard:

   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("slot-tracker-release.jks")) | Set-Clipboard`
   - macOS: `base64 -i slot-tracker-release.jks | pbcopy`
   - Linux: `base64 -w0 slot-tracker-release.jks`

   Or, with the GitHub CLI (Git Bash, macOS or Linux): `base64 -w0 slot-tracker-release.jks | gh secret set KEYSTORE_BASE64`
   (on macOS use `base64 -i`), then `gh secret set KEYSTORE_PASSWORD`, which prompts for the value, and likewise for the other two.

The build fails with a clear error if any secret is missing.

## Versions and releases

- `versionCode` is the CI run number, so every build can update the previous one.
- Pushes to `main` are named `0.0.0-dev.<run number>`.
- To publish a release, push a tag such as `v1.0.0`. CI builds `1.0.0` and attaches the APK to a GitHub Release.

## Building locally

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`).

```sh
./gradlew testDebugUnitTest   # unit tests for day / rollover / streak logic
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # release APK (unsigned unless the SIGNING_* environment variables are set)
```
