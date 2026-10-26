# Quiet Home

A native Android launcher: black background, a clock, and app names with small generated monochrome marks. Home includes a small app-grid icon for **Everything else** and a phone-wide monochrome/color toggle. No feed, branded app icons, badges, ads, analytics, or network permission.

## Configure home in code

Edit `HomeApps.PACKAGES` in `app/src/main/java/dk/natmad/home/HomeApps.java`, rebuild, and install the updated APK. Each row contains only package IDs: one home slot with optional alternatives. App names are read from Android's installed application metadata. The current package order is below; the names in this table are descriptive, not configuration:

| Label | Package ID |
| --- | --- |
| Phone | `com.google.android.dialer`, then `com.android.dialer` |
| [Rejsekort](https://play.google.com/store/apps/details?id=dk.rejsekort.digitalrejsekort) | `dk.rejsekort.digitalrejsekort` |
| Obsidian | `md.obsidian` |
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

Tap the small split-circle icon to the right of the clock for the one-time setup. Android does not let ordinary apps change the whole phone's color mode through a runtime permission dialog. With USB debugging enabled and the phone connected to a computer with Android platform tools, run:

```sh
adb shell pm grant dk.natmad.home android.permission.WRITE_SECURE_SETTINGS
```

Return to Quiet Home: monochrome is now the default across the whole phone, including other apps. Tap the icon beside the clock to switch colors on or off. The 24dp icon has a 48dp touch target, an accessibility label, and a long-press tooltip. There is no visible button text, countdown, or return time.

Quiet Home saves the time colors were enabled in device-protected storage. When you return to the launcher, it compares that timestamp with the current time: if it is at least 12 hours old, monochrome is restored. Colors stay on while you are in another app, even beyond 12 hours, until you return home. There is no background alarm, countdown, or Alarms & reminders permission. Checks never rewrite the timestamp. An invalid or future timestamp falls back to monochrome. Existing color sessions migrate from the older deadline format without starting a fresh 12 hours; old background alarms are cancelled.

This uses Android's AOSP secure color-correction settings and replaces existing color correction. Manufacturer behavior still needs physical-device testing. Quiet Home needs its display permission to restore monochrome. After a restart, the timestamp is checked the next time the launcher opens. Removing Quiet Home does not undo the system color setting. To disable grayscale manually, use Developer options → Simulate color space → Disabled, or run `adb shell settings put secure accessibility_display_daltonizer_enabled 0`.

Implementation reference: [AOSP display adjustment](https://android.googlesource.com/platform/frameworks/base/+/3625bf7/services/accessibility/java/com/android/server/accessibility/DisplayAdjustmentUtils.java).

## Alarms

Tap the small, muted alarm icon on the same row as the date to open your alarms. Both header icons use a 24dp drawing in a 48dp touch target and align in one column at the trailing edge. When an alarm is scheduled, a compact day and time appears to the left of the alarm icon (for example, **Mon 07:00**), following the phone's 12/24-hour setting. With no alarm set, only the icon remains in the same position. There is no box or extra visible label, and screen readers announce the full next-alarm date and time or the empty state.

The indicator uses Android's [next alarm clock](https://developer.android.com/reference/android/app/AlarmManager#getNextAlarmClock()) for the current user. Alarm apps must publish their alarms through Android's alarm-clock API to appear here; ordinary timers, calendar reminders, and apps that do not publish an alarm clock are not included. No new permission or setup is required. The row updates while home is visible and refreshes on return, including after alarm, clock, or time-zone changes.

Opening first uses Android's [Show alarms action](https://developer.android.com/reference/android/provider/AlarmClock#ACTION_SHOW_ALARMS), then discovers apps that support showing or setting alarms and opens their normal app entry point. This launch order stays the same whether an alarm is scheduled or not. The next alarm's own shortcut is used only if no Clock app can be opened normally, because a vendor shortcut can accept the request without opening a screen. Discovery does not create an alarm or require an additional permission. If no usable entry point exists, Quiet Home displays a message.

For OnePlus phones, the launcher also checks the official [OnePlus Clock package](https://play.google.com/store/apps/details?id=com.oneplus.deskclock) (`com.oneplus.deskclock`) before trying the alarm's own shortcut, even if it advertises neither standard alarm action. Android resolves its current launch activity; missing or disabled apps are skipped.

## Music controls

Open **app-grid icon → Music controls setup**, then grant notification access to **Quiet Home music controls** in Android Settings. This is Android's permission for discovering other apps' [active media sessions](https://developer.android.com/reference/android/media/session/MediaSessionManager). Quiet Home does not read or store notification contents.

While a player publishes a playing or paused media session, the bottom section above Everything else can be swiped horizontally between **Music** and **Små Mål**. The boxes scroll horizontally with your finger and settle onto a fixed position when you release; a quick flick advances to the next box. There are no tabs. Screen readers can use the scroll-forward/backward actions, and keyboard users can use left/right arrows. Music shows the track, artist, and round Previous / Play or Pause / Next / Stop icon controls. Without a playing or paused session, the bottom section shows only **Små Mål**, with no empty music page. Play/pause is highlighted in white; the other controls have a fine outline. Each icon has an accessibility label and a long-press tooltip. Paused playback remains available to resume; stopped or ended sessions disappear. Unsupported controls are disabled. If several players have sessions, playing media takes priority over paused media. Track details update without rebuilding the home screen. Players must expose Android media sessions; this can include podcasts and video as well as music.

Stop sends [Android's media-session stop command](https://developer.android.com/reference/android/media/session/MediaController.TransportControls#stop()) to the selected player, including when paused. The home controls disappear once that player reports stopped playback or ends its session. Whether it also removes its notification or closes its service is up to the player; Quiet Home cannot force-close another app. Stop is dimmed if the player does not support it.

Notification access is optional and can be revoked in Android Settings. Without it, the bottom section shows only Små Mål; music setup remains available in Everything else. Quiet Home observes session changes only while the home screen is in the foreground.

## Små Mål

The bottom section shows just the goals and **+ Add**, without a header, counter, or placeholder text. It fits its contents, showing up to two standard-height goal rows before scrolling; an empty list takes only one button-height row. It holds small goals for today, such as “Go for a run”, “Go for a swim”, or “Finish task A”. Tap the discreet **+ Add** button to reveal an inline field, then save with **Add** or the keyboard’s Done key. Saving or cancelling hides the field again. Check a goal off when done, uncheck it to reopen it, or long-press a goal to rename or delete it in the same inline field. Save, Cancel, and Delete stay in the section; no dialog opens. Cancelling an edit restores any new-goal draft you were writing. Drafts and in-progress edits survive swiping, returning home, and activity recreation. The count shows completed goals out of today's total. Completed goals remain visible and checked off until the local calendar day changes.

Goals and completion are saved locally across restarts. Each local calendar day has its own list; unfinished goals do not automatically carry forward. Earlier days remain stored locally but are not shown in today's section. The date refreshes while home is visible and on return. There is no account, sync, notification, or extra permission.

The app list remains separate and stationary while the bottom section switches between music and goals. Obsidian replaces Garmin Connect in the existing home slot; Garmin remains available in Everything else when installed.

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
- **Everything else:** tap the muted app-grid icon at the bottom of home to open an alphabetical list of all launchable installed apps, including those on home. Chrome and Maps remain available here. Tap an app to open it.
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
adb -s emulator-5554 shell am instrument -w dk.natmad.home.test/dk.natmad.home.LauncherSmokeTest
```

The rule checks cover search normalization, exact package classification, duplicate category membership, and timestamp age, the 12-hour boundary, and invalid timestamps. Display instrumentation checks the actual secure settings, the compact accessible icon, manual reset, timestamp persistence, upgrade migration, preserving colors in another app, and restoring monochrome on return home after the saved timestamp expires. These checks leave the emulator in monochrome. The instrumentation test verifies home ordering, the full alphabetical drawer, search, and a real YouTube launch attempt when installed. It also runs the actual 30-second countdown and 3-second hold, checking early activation, release/cancellation, focus loss, duplicate activation, detaching the view, and stable button placement with narrow layouts and large text. Small-goal instrumentation checks empty-input rejection, adding, completion and reopening, persistence after rebuilding home, editing, deletion, and separation of calendar days. Scroll checks verify finger-following geometry before release, animated snap-back after cancellation or a short drag, advancing with a fling, accessible scrolling, draft preservation, and that stopping playback leaves only goals. Real test media sessions exercise play/pause, previous/next, Stop during playing and paused playback, metadata updates, unsupported actions, session priority, stopped/released sessions, and returning home. They do not play audio. The emulator needs at least one configured home app installed, notification access enabled as above, and touch-exploration off for physical-hold tests. Revoke the test grant afterwards with `adb -s emulator-5554 shell cmd notification disallow_listener dk.natmad.home/dk.natmad.home.MediaAccessService`.

Alarm instrumentation checks the shared date/alarm row against the system's next alarm, then uses controlled alarm data and broadcasts to check empty/scheduled/cancelled states, opening alarms, missing or permission-protected Show alarms actions, a missing alarm app, 12/24-hour and time-zone changes, and receiver cleanup on stop and detach. A launchable alarm app must be installed for the fallback checks. It restores the time format after testing.

Physical-device behavior and Android 8–13/15+ still need testing. Lint may report non-blocking notices about newer SDK/Gradle versions and backup configuration. Earlier screenshots in `docs/` show previous versions.
