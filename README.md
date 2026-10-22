# Quiet Home

A native Android launcher: black background, a clock, and app names with small generated monochrome marks. Home includes **Everything else** and a phone-wide monochrome/color toggle. No feed, branded app icons, badges, ads, analytics, or network permission.

## Configure home in code

Edit `HomeApps.PACKAGES` in `app/src/main/java/dk/natmad/home/HomeApps.java`, rebuild, and install the updated APK. Each row contains only package IDs: one home slot with optional alternatives. App names are read from Android's installed application metadata. The current package order is below; the names in this table are descriptive, not configuration:

| Label | Package ID |
| --- | --- |
| Phone | `com.google.android.dialer`, then `com.android.dialer` |
| [Rejsekort](https://play.google.com/store/apps/details?id=dk.rejsekort.digitalrejsekort) | `dk.rejsekort.digitalrejsekort` |
| Garmin Connect | `com.garmin.android.apps.connectmobile` |
| Tally | `com.madsbuch.tally` |
| Varg | `com.varg.workout` |

Android resolves each package's launch activity with `getLaunchIntentForPackage` and its display name with `ApplicationInfo.loadLabel`. The first launchable alternative in a row wins. Names follow the app's own localized label; there is no hardcoded name or matching by app name. Renaming an app, changing language, or updating its launch activity does not change which package is selected. Missing/unlaunchable packages are omitted from home. Everything else includes all launchable apps, including those on home; Quiet Home itself is omitted.

If your phone uses a different dialer (for example, a manufacturer-specific one), add its exact package ID to the Phone entry. There is no name-based fallback. The list is rebuilt from the code whenever the launcher resumes. There is no runtime editor or saved favorite override: older versions' organization preferences are ignored.

Tally's ID is confirmed in its [Android build configuration](https://github.com/madsbuch/tally-tauri/blob/HEAD/src-tauri/gen/android/app/build.gradle.kts); Varg's identifier comes from its [Tauri configuration](https://github.com/madsbuch/varg/blob/HEAD/src-tauri/tauri.conf.json).

## Try it on your phone

1. On your phone, open the [latest GitHub release](https://github.com/madsbuch/home-screen/releases/tag/latest) and download **quiet-home.apk** from Assets. Open the downloaded APK. Android may ask you to allow installation from your browser or file manager.
2. Open **Quiet Home** to explore it.
3. In Android Settings, find **Default apps → Home app** and select **Quiet Home**. Settings labels vary by phone.
4. To switch back, select your previous launcher in the same Android setting.

Requires Android 8.0 or later. GitHub publishes a release APK signed with a persistent, app-specific key. Future GitHub builds can be installed over it as updates. If you already installed the earlier local debug APK, uninstall that once before switching to the GitHub release; it has a different signature. After switching, select Quiet Home as the default again if needed.

For a local development build, transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone instead. Keep debug builds separate from the release installation.

## Monochrome and colors

Tap **Set up monochrome** on home for the one-time setup. Android does not let ordinary apps change the whole phone's color mode through a runtime permission dialog. With USB debugging enabled and the phone connected to a computer with Android platform tools, run:

```sh
adb shell pm grant dk.natmad.home android.permission.WRITE_SECURE_SETTINGS
```

Return to Quiet Home: monochrome is now the default across the whole phone, including other apps. Tap **Colors for 12 hours** to temporarily enable colors. On Android 12 and later, the first use directs you to allow **Alarms & reminders**; return and tap the button again. **Use monochrome** switches back immediately and cancels the current window. Each new color window lasts at most 12 hours, with its return time shown below the button. Quiet Home's own minimal styling stays the same.

The deadline is saved in device-protected storage. An exact wake-up alarm restores monochrome even when Quiet Home is in the background or the phone is asleep. Restarting the launcher does not extend the deadline; boot and app-update receivers restore it, including before first unlock. Elapsed time prevents a backwards clock change from extending a window during the same boot. After reboot, the saved wall-clock deadline is used; an invalid deadline falls back to monochrome.

This uses Android's AOSP secure color-correction settings and replaces existing color correction. Manufacturer behavior still needs physical-device testing. As with other Android alarms, force-stopping the app or revoking its permissions can prevent the reset; reopening Quiet Home reconciles the deadline if display access remains granted. A powered-off phone reconciles on boot. Removing Quiet Home does not undo the system color setting. To disable grayscale manually, use Developer options → Simulate color space → Disabled, or run `adb shell settings put secure accessibility_display_daltonizer_enabled 0`.

Implementation references: [Android exact alarms](https://developer.android.com/develop/background-work/services/alarms) and [AOSP display adjustment](https://android.googlesource.com/platform/frameworks/base/+/3625bf7/services/accessibility/java/com/android/server/accessibility/DisplayAdjustmentUtils.java).

## Music controls

Tap **Enable music controls** on home, then grant notification access to **Quiet Home music controls** in Android Settings. This is Android's permission for discovering other apps' [active media sessions](https://developer.android.com/reference/android/media/session/MediaSessionManager). Quiet Home does not read or store notification contents.

While a player publishes a playing or paused media session, home shows the track, artist, and round Previous / Play or Pause / Next / Stop icon controls above Everything else. Play/pause is highlighted in white; the other controls have a fine outline. Each icon has an accessibility label and a long-press tooltip. Paused playback remains available to resume; stopped or ended sessions disappear. Unsupported controls are disabled. If several players have sessions, playing media takes priority over paused media. Track details update without rebuilding the home screen. Players must expose Android media sessions; this can include podcasts and video as well as music.

Stop sends [Android's media-session stop command](https://developer.android.com/reference/android/media/session/MediaController.TransportControls#stop()) to the selected player, including when paused. The home controls disappear once that player reports stopped playback or ends its session. Whether it also removes its notification or closes its service is up to the player; Quiet Home cannot force-close another app. Stop is dimmed if the player does not support it.

Notification access is optional and can be revoked in Android Settings. Without it, home shows the setup button. Quiet Home observes session changes only while the home screen is in the foreground.

## Opening friction

Edit `AppFriction.java` to change package membership or durations; there is no organization UI. Every app stays in the alphabetical, searchable drawer. These checks apply to both home and drawer launches:

| Category | Behavior | Configured apps |
| --- | --- | --- |
| Hard | Wait 30 seconds, then tap Open | YouTube, Instagram (including Lite), Facebook (including Lite), Threads, TikTok (global and regional), Reddit, X, Snapchat, Pinterest, LinkedIn, Bluesky, Mastodon, Tumblr |
| Slight | Hold the opening button continuously for 3 seconds | ChatGPT, Claude, Gemini, Perplexity, Copilot, Grok, DeepSeek, Poe, Mistral, Character.AI |
| Other | Open immediately | All other package IDs, including Signal, WhatsApp, and your everyday home apps |

Releasing early, moving outside the button, or cancelling a touch resets the hold. Leaving the confirmation screen discards the attempt; losing window focus resets its timer. The hard countdown never opens an app automatically. Go back returns to the previous list and preserves the search query. Keyboard users can hold Enter, Space, or the center navigation key; TalkBack users activate once, wait 3 seconds, then activate again.

The status area reserves enough space for every instruction and countdown message at the current screen width and font size, keeping the opening button in the same position as the text changes. The opening button uses a white rounded pill, with an outlined Go back button below it.

The package lists cover the named apps, not every possible third-party client or future chat app. Add their exact IDs in code as needed. Package IDs can be checked in the `id=` parameter of an app's official Play Store link, for example [YouTube](https://play.google.com/store/apps/details?id=com.google.android.youtube), [Claude](https://play.google.com/store/apps/details?id=com.anthropic.claude), and [Gemini](https://play.google.com/store/apps/details?id=com.google.android.apps.bard).

This is friction inside Quiet Home, not a system-wide app blocker. Notifications, links, Recents, assistant gestures, and websites can bypass it. Opening friction itself requires no extra permission, phone connection, or background monitoring. The optional phone-wide grayscale control has its own setup described above.

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

- **Home:** the code-defined app list. Apps open immediately unless assigned opening friction. The clock follows the phone's 12/24-hour setting.
- **Everything else:** an alphabetical list of all launchable installed apps, including those on home. Chrome and Maps remain available here. Tap an app to open it.
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
adb -s emulator-5554 shell cmd notification allow_listener dk.natmad.home/dk.natmad.home.MediaAccessService
adb -s emulator-5554 shell pm grant dk.natmad.home android.permission.WRITE_SECURE_SETTINGS
adb -s emulator-5554 shell appops set dk.natmad.home SCHEDULE_EXACT_ALARM allow
adb -s emulator-5554 shell am instrument -w dk.natmad.home.test/dk.natmad.home.LauncherSmokeTest
```

The rule checks cover search normalization, exact package classification, duplicate category membership, and color-window expiry boundaries, clock changes, and reboot deadlines. Display instrumentation checks the actual secure settings, manual reset, deadline persistence, reboot recovery, and an accelerated OS alarm while another app is foregrounded. These checks leave the emulator in monochrome. The instrumentation test verifies home ordering, the full alphabetical drawer, search, and a real YouTube launch attempt when installed. It also runs the actual 30-second countdown and 3-second hold, checking early activation, release/cancellation, focus loss, duplicate activation, detaching the view, and stable button placement with narrow layouts and large text. Real test media sessions exercise play/pause, previous/next, Stop during playing and paused playback, metadata updates, unsupported actions, session priority, stopped/released sessions, and returning home. They do not play audio. The emulator needs at least one configured home app installed, notification access enabled as above, and touch-exploration off for physical-hold tests. Revoke the test grant afterwards with `adb -s emulator-5554 shell cmd notification disallow_listener dk.natmad.home/dk.natmad.home.MediaAccessService`.

Physical-device behavior and Android 8–13/15+ still need testing. Lint may report non-blocking notices about newer SDK/Gradle versions and backup configuration. Earlier screenshots in `docs/` show previous versions.
