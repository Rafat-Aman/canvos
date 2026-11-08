<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo_canvos.png" width="120" alt="Canvos logo">
</p>

<h1 align="center">Canvos</h1>

<p align="center">
  An unofficial, open-source Android app for <a href="https://www.instructure.com/canvas">Canvas LMS</a> students.<br>
  Sign in with a Canvas access token. No ads, no analytics, about 2.5 MB.
</p>

<p align="center">
  <a href="https://github.com/Rafat-Aman/canvos/releases/latest"><img src="https://img.shields.io/github/v/release/Rafat-Aman/canvos?label=download" alt="Latest release"></a>
  <a href="https://github.com/Rafat-Aman/canvos/actions/workflows/ci.yml"><img src="https://github.com/Rafat-Aman/canvos/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Rafat-Aman/canvos" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
</p>

> [!NOTE]
> Canvos is not made by, affiliated with or endorsed by Instructure. Canvas is a trademark of Instructure, Inc.
> The prebuilt APK signs in to **North South University's** Canvas. For another school, see
> [Building for another school](#building-for-another-school).

## Why

Some schools only let students into Canvas through a web or Google sign-in that doesn't work well
in the official app. Canvas also lets every user create a personal **access token**. Canvos signs in
with that token, so you paste it once and you're in.

## Features

| Area | What you get |
|---|---|
| **Dashboard** | Course cards in your Canvas colours and images, grades, a "Due soon" strip, nicknames and colours, and an All courses list where you star courses to the dashboard |
| **Courses** | Home (opens the course's default view), Announcements, Assignments (by due date or group), Discussions with threaded replies, Grades (current, or counting ungraded as 0), Modules with completion state, Pages, Files (open, share, save), Syllabus, People (message anyone), Quizzes, and any other tab or external tool in a signed-in web view |
| **Assignments** | Details, submitting **text, a URL or file uploads**, resubmitting, and your submission with feedback, rubric and comments |
| **Calendar** | Month grid with course-coloured dots, day agenda, event details |
| **Google Calendar sync** | Keeps due dates and events in one of your phone's Google calendars, with reminders. Moved due dates update, deleted items are removed, and events you delete stay deleted. You can also subscribe to your Canvas calendar feed in one tap |
| **To Do** | Planner items by day, an overdue section, done checkboxes, course filter |
| **Notifications** | Activity stream with filters. Pop-up notifications for new announcements, messages, grades and feedback, and discussions, plus due-date reminders |
| **Offline** | Chosen courses are saved on the phone (pages, assignments, announcements, discussions, grades, modules, people, inbox, calendar and files) and kept up to date over Wi-Fi |
| **Inbox** | Inbox, Unread, Starred, Sent and Archived, course filter, threads, reply, compose with recipient search, star, archive, mark unread |
| **Other** | Light, dark or system theme, Material You colours, quizzes and LTI tools in a web view signed in with your token, file downloads with progress |

## Install

1. Download `canvos-<version>.apk` from the [latest release](https://github.com/Rafat-Aman/canvos/releases/latest).
2. Open it on your phone and allow installing from your browser or file manager when Android asks.
3. In Canvas on the web, go to **Account → Settings → + New Access Token**. Give it a purpose
   (for example "Canvos") and an expiry date, then copy the token. Canvas shows it only once.
4. Open Canvos, paste the token and tap **Sign in**.

When the token expires, Canvos asks you to sign in again with a new one.

Each release lists the APK's SHA-256 signing-certificate fingerprint, so you can check that an APK
really comes from this project:

```
BD:A8:66:76:F8:5C:C8:55:D9:A7:DB:FA:99:E7:CB:12:14:51:96:90:98:4C:EC:40:C1:02:E4:5C:AE:33:10:93
```

## Privacy and security

- The token is encrypted with a key in the Android Keystore and is excluded from backups.
- The token is only ever sent to your school's Canvas site, never to file-storage hosts or other sites.
- There are no ads, analytics, crash reporters or third-party services. The app only talks to your Canvas site
  and to the hosts Canvas sends files from.
- Logging out deletes the token and all offline content from the phone.

See [PRIVACY.md](PRIVACY.md) for what the app stores and why it needs each permission, and
[SECURITY.md](SECURITY.md) for reporting vulnerabilities.

## Notifications

Canvas only sends push notifications to Instructure's own app. Canvos therefore checks for news
every 15 minutes, Android's shortest background interval, and shows it as a normal notification.
Each type has its own channel (Announcements, Inbox messages, Grades and feedback, Due date
reminders, Discussions, Other), so you can set sounds per type in Android settings.

- On first start Canvos asks for the notification permission and offers to lift battery limits.
  Without that, Android may hold the checks back for hours.
- **Settings → Notifications** shows when the last check ran, warns if notifications or background
  activity are blocked, and can send a test notification.
- Due-date reminders (by default 1 day and 1 hour before) cover anything you haven't handed in.
- Notifications aren't instant: expect up to 15 minutes' delay, more when the phone is in deep sleep.
  For instant alerts, also turn on Canvas's own email notifications.

## Offline content

Open **Drawer → Offline content**. By default every dashboard course is kept offline: each screen's
data, images used in pages, and course files up to 100 MB each. Updates run on Wi-Fi every 3 hours,
when Wi-Fi connects after that, and when you open the app if the last update is over an hour old.
Unchanged items are skipped, so later updates are quick.

- Files are stored in `Android/data/com.rafat.canvos/files/Canvas/<course>/<folder>/` and are listed
  under **Downloaded files**.
- Without a connection, each screen shows its saved copy with an "Offline" strip, and refreshes by
  itself when the connection returns.
- You can pick courses (including past ones), set Wi-Fi only, set a file-size limit, or remove all
  offline content.
- Saving content counts as viewing it in Canvas (teachers' access reports and "must view" module
  steps), the same as the official app's offline mode.

## Building

You need JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
git clone https://github.com/Rafat-Aman/canvos.git
cd canvos
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android lint
```

If Gradle can't find the SDK, create `local.properties` with `sdk.dir=/path/to/Android/Sdk`.

### Release builds

`./gradlew assembleRelease` writes `app/build/outputs/apk/release/canvos-<version>-release.apk`.
It's minified with R8 and signed with your release key, if you've set one up. Without one, it falls
back to the debug key so you can still install it.

To sign with your own key, create `keystore.properties` in the project root. Git ignores this file.

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

CI reads the same values from the `CANVOS_KEYSTORE_FILE`, `CANVOS_KEYSTORE_PASSWORD`,
`CANVOS_KEY_ALIAS` and `CANVOS_KEY_PASSWORD` environment variables.

### Building for another school

The school is a build setting. Add these lines to `gradle.properties`, or pass them as `-P` flags:

```properties
canvos.schoolUrl=https://yourschool.instructure.com
canvos.schoolName=Your University
canvos.schoolShortName=YU
```

If you share a build for another school, change `applicationId` in `app/build.gradle.kts` too, so it
can't be confused with the official one.

### Publishing a release (maintainers)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, and add the changes to
   [CHANGELOG.md](CHANGELOG.md) and `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Commit, then tag and push: `git tag v1.0.1 && git push origin v1.0.1`.
3. The [release workflow](.github/workflows/release.yml) builds a signed APK and drafts a GitHub
   release with it attached. It needs these repository secrets:

   | Secret | Value |
   |---|---|
   | `CANVOS_KEYSTORE_BASE64` | `base64 -w0 your-release.jks` |
   | `CANVOS_KEYSTORE_PASSWORD` | keystore password |
   | `CANVOS_KEY_ALIAS` | key alias |
   | `CANVOS_KEY_PASSWORD` | key password |

4. Review the draft on GitHub and publish it.

## Tests

- **`OfflineTest`** covers saved copies (used only when offline or when Canvas is down, never by background
  jobs), multi-page lists, the planner date-range fallback, folder trees, HTML image and file links,
  notification and reminder rules, and telling "not authorised" apart from an expired token.
- **`LogicTest`** covers link routing, paging, error parsing, grade and status logic, and the three-step file
  upload against a fake server. It checks that the token never reaches the storage host.
- **`LiveCanvasTest`** calls every read endpoint the app uses against a real account, through a client that
  refuses all writes. It runs only when these are set:
  `CANVAS_URL=https://school.instructure.com CANVAS_TOKEN=… ./gradlew testDebugUnitTest`
- **`CalendarSyncTest`** runs on a device. It tests a full sync against a local, non-syncing calendar:
  `./gradlew installDebug installDebugAndroidTest && adb shell am instrument -w com.rafat.canvos.test/androidx.test.runner.AndroidJUnitRunner`

Debug builds have **Settings → Developer → Read-only mode**, which blocks every change to Canvas.
Use it whenever you test against a real account.

## Tech stack

Kotlin, Jetpack Compose with Material 3, OkHttp, kotlinx.serialization, Coil and WorkManager.
Min SDK 26 (Android 8.0), target SDK 37.

```
app/src/main/java/com/rafat/canvos/
├── data/            Canvas API client, repository, session, notifications, calendar sync
│   ├── api/         HTTP client and data models
│   └── offline/     offline store, disk cache and sync
├── ui/              Compose screens (dashboard, course, calendar, inbox, todo, settings, …)
└── util/            formatting helpers
```

## Contributing

Bug reports, ideas and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first,
and please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Canvos is released under the [MIT License](LICENSE).
