# Contributing to Canvos

Thanks for helping. Bug reports, ideas and pull requests are all welcome.

## Reporting bugs and asking for features

Use the [issue forms](https://github.com/Rafat-Aman/canvos/issues/new/choose). For bugs, include your
Canvos version (shown at the bottom of Settings), your Android version and phone model, and steps to reproduce.

**Never paste your access token**, or screenshots that show it, into an issue. If a bug is about
security, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.

## Development setup

1. Install Android Studio (or JDK 17+ and the Android SDK).
2. Clone the repo and open it in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest lintDebug
   ```
3. To test against a real Canvas account, use a debug build and turn on
   **Settings → Developer → Read-only mode**. It blocks every change to Canvas, so you can't
   accidentally submit, post or send anything.

## Pull requests

- Keep each pull request to one change, and explain what it does and why.
- Follow the existing style. The project uses the official Kotlin code style
  (`kotlin.code.style=official`) and the settings in `.editorconfig`.
- Add or update tests in `app/src/test` for logic changes. `./gradlew testDebugUnitTest lintDebug`
  must pass, and CI runs both.
- Update [README.md](README.md) if you change behaviour users can see, and add a line under
  **Unreleased** in [CHANGELOG.md](CHANGELOG.md).
- Don't add analytics, ads, crash reporters or other third-party tracking. Keeping the app free of
  them is a goal of the project.
- The token must only ever be sent to the user's Canvas host. Don't send it to file-storage hosts,
  redirects or anywhere else.

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
