# Glass Keys (starter project)

A privacy-first Android IME starter inspired by the soft, frosted-key look of modern iOS. It is not Apple's keyboard and does not reproduce proprietary iOS code/assets.

## Features in this first source version
- Turkish Q key layout
- Basic letters, shift, symbol toggle, backspace, space, enter/action key
- Lightweight key haptics
- Android system keyboard service (IME)
- No internet permission and no keystroke storage

## Build in the cloud (no PC)
This project includes `.github/workflows/build-apk.yml`. Once the project files are committed to a GitHub repository, GitHub Actions builds a debug APK automatically on pushes to `main`/`master`, and can also be started manually from the Actions tab. Download the `GlassKeys-debug-apk` artifact from the successful run.

The workflow uses Java 17 and Gradle 8.9 on a GitHub-hosted runner. It creates a debug APK for testing, not a Play Store release.

## Install on Android
1. Download `app-debug.apk` from the Actions artifact and extract the downloaded artifact ZIP if needed.
2. Open the APK on the HONOR phone and approve installation from that source if Android asks.
3. Open Glass Keys, tap **Klavye etkinleştir**, enable it in system settings, then tap **Glass Keys'i seç** and choose it.

## Features not implemented yet
This is an early prototype, not a tested release APK. The exact iOS blur, prediction engine, emoji panel, long-press alternatives and clipboard features are not implemented yet. Review and test before everyday use.
