# Quiet Home

A native Android launcher: black background, a clock, and app names with small generated monochrome marks. The only home-screen navigation is **Everything else**. No feed, branded app icons, badges, ads, analytics, or network permission.

## Configure home in code

Edit `HomeApps.PACKAGES` in `app/src/main/java/dk/natmad/home/HomeApps.java`, rebuild, and install the updated APK. Each row contains only package IDs: one home slot with optional alternatives. App names are read from Android's installed application metadata. The current package order is below; the names in this table are descriptive, not configuration:

| Label | Package ID |
| --- | --- |
| Signal | `org.thoughtcrime.securesms` |
| Chrome | `com.android.chrome` |
| Phone | `com.google.android.dialer`, then `com.android.dialer` |
| Maps | `com.google.android.apps.maps` |
| FastMail | `com.fastmail.app` |
| Garmin Connect | `com.garmin.android.apps.connectmobile` |
| Tally | `com.madsbuch.tally` |
| Varg | `com.varg.workout` |

Android resolves each package's launch activity with `getLaunchIntentForPackage` and its display name with `ApplicationInfo.loadLabel`. The first launchable alternative in a row wins. Names follow the app's own localized label; there is no hardcoded name or matching by app name. Renaming an app, changing language, or updating its launch activity does not change which package is selected. Missing/unlaunchable packages are omitted; all launcher entries from a selected package are excluded from Everything else.

If your phone uses a different dialer (for example, a manufacturer-specific one), add its exact package ID to the Phone entry. There is no name-based fallback. The list is rebuilt from the code whenever the launcher resumes. There is no runtime editor or saved favorite override: older versions' organization preferences are ignored.

Tally's ID is confirmed in its [Android build configuration](https://github.com/madsbuch/tally-tauri/blob/HEAD/src-tauri/gen/android/app/build.gradle.kts); Varg's identifier comes from its [Tauri configuration](https://github.com/madsbuch/varg/blob/HEAD/src-tauri/tauri.conf.json).

## Try it on your phone

1. On your phone, open the [latest GitHub release](https://github.com/madsbuch/home-screen/releases/tag/latest) and download **quiet-home.apk** from Assets. Open the downloaded APK. Android may ask you to allow installation from your browser or file manager.
2. Open **Quiet Home** to explore it.
3. In Android Settings, find **Default apps → Home app** and select **Quiet Home**. Settings labels vary by phone.
4. To switch back, select your previous launcher in the same Android setting.

Requires Android 8.0 or later. GitHub publishes a release APK signed with a persistent, app-specific key. Future GitHub builds can be installed over it as updates. If you already installed the earlier local debug APK, uninstall that once before switching to the GitHub release; it has a different signature. After switching, select Quiet Home as the default again if needed.

For a local development build, transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone instead. Keep debug builds separate from the release installation.

## GitHub builds

Every push to `main` runs the search checks and Android lint, builds a signed release APK, verifies its signature, and updates the rolling [latest release](https://github.com/madsbuch/home-screen/releases/tag/latest). You can also trigger **Android APK → Run workflow** from Actions. Pull requests run checks without signing credentials or publication.

The workflow is `.github/workflows/android.yml`. It publishes `quiet-home.apk` and its SHA-256 checksum. `VERSION_CODE` is `10000 + GITHUB_RUN_NUMBER`, so newer workflow runs install as Android updates without manually changing the app version. Keep the workflow's run-number history; if replacing the workflow with a new one, increase the offset beyond the largest published code.

### Signing setup

Run once with a JDK and an authenticated GitHub CLI:

```sh
python3 scripts/setup-signing.py --repo madsbuch/home-screen
```

The script creates an app-specific signing key in the ignored `.signing/` directory and uploads four encrypted repository secrets: `ANDROID_KEY_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and `ANDROID_KEYSTORE_PASSWORD`. Re-running reuses the existing key. Back up the entire `.signing/` directory securely; it is deliberately excluded from Git. Never replace the key after distributing the app, or installed copies will reject in-place updates. The workflow fails if signing secrets are missing instead of publishing an unsigned or randomly signed APK.

## How it works

- **Home:** the code-defined app list, one tap to open. The clock follows the phone's 12/24-hour setting.
- **Everything else:** an alphabetical list of the remaining installed apps, including anything previously classified as a distraction. Tap an app to open it.
- **Search apps:** filters that list as you type, matching any part of a name regardless of capitalization. Clear restores the full list; returning home resets the query. Opening the list focuses search and requests the keyboard.

Each app has a muted 24dp identicon on an 8×8 grid. SHA-256 of the Android package identifier supplies four bits per row, mirrored horizontally into eight columns. The mark stays the same across restarts, label/language changes, and launcher activity changes. Apps from the same package share a mark. These are visual cues, not guaranteed unique identifiers; the app name remains visible. Drawing uses native Android Canvas with no image assets or downloads.

This first version lists apps in the current Android profile; work profiles, Private Space, widgets, and notification badges are not implemented.

## Build

Open this directory in Android Studio, or use JDK 17+ and Android SDK 35:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
# On this Mac, Android Studio includes a suitable JDK:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug lintDebug
```

The Gradle wrapper is included. The app uses Java and Android framework views, with no third-party runtime dependencies. Name matching and search are in `AppRules.java`; generated marks are in `AppIdenticon.java`.

## Verify

```sh
sh scripts/test-rules.sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w dk.natmad.home.test/dk.natmad.home.LauncherSmokeTest
```

The rule checks cover partial search, casing, whitespace, Unicode equivalence, and no matches. The instrumentation test verifies home ordering from configured package IDs, filtering against installed apps, empty results, clearing, and resetting search after returning home. The emulator needs at least one non-home app installed.

Physical-device behavior and Android 8–13/15+ still need testing. Lint may report non-blocking notices about newer SDK/Gradle versions and backup configuration. Earlier screenshots in `docs/` show previous versions.
