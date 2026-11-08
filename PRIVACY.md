# Privacy

Canvos has no servers of its own, and it has no ads, analytics, crash reporting or other third-party SDKs.

## What the app stores on your phone

| Data | Where | Removed when |
|---|---|---|
| Your Canvas site and access token | App storage, encrypted with an Android Keystore key, excluded from backups | You log out or uninstall |
| Offline copies of courses and files you choose | App storage and `Android/data/com.rafat.canvos/files/` | You remove offline content, log out or uninstall |
| Settings (theme, notification and sync options) | App storage | You uninstall |

## Who the app talks to

- **Your school's Canvas site**, using your token, for everything the app shows and does.
- **The file hosts Canvas links to**, for example Instructure's file storage, to download files and
  images. Your token is never sent to them.
- **Web pages you open** inside the app, such as quizzes and external tools.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | Talk to Canvas, and update offline content only on Wi-Fi if you choose |
| Notifications | Show new announcements, messages, grades and reminders |
| Ignore battery optimisations | Optional. Lets the 15-minute checks for news run on time |
| Read and write calendar | Only if you turn on Google Calendar sync. Adds your due dates to the calendar you pick |

Calendar sync writes to the Google calendar you pick on your phone. Google then syncs that calendar
under its own privacy policy, as it does any other event you add.
