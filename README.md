# Glass Keys

Glass Keys is an Android input method (IME) with a Turkish Q keyboard and a translucent, glass-inspired appearance. It is an independent Android implementation; it does not contain Apple's proprietary keyboard code or assets.

## Implemented features

- Turkish Q layout, shift/caps lock, symbols, numeric input layout, emoji panel and editor action keys.
- Long-press alternatives for selected letters.
- Configurable system/light/dark theme, key opacity, roundness, vibration and optional number row.
- Offline word suggestions using a bundled Turkish word list, small context rules and locally learned words/word pairs.
- Single-edit prefix matching for a limited class of typing mistakes. This is not a full spell checker or neural language model.
- Local personal-learning toggle, learned-word list and controls to clear or delete learned words.
- Password and NO_SUGGESTIONS fields suppress suggestions and learning. Email/URI fields disable personal learning.
- No network permission is declared. Personal words and adjacent-word counts are stored locally in app preferences; raw keystroke sequences are not intentionally stored.
- Backspace uses Unicode code-point deletion where the target editor supports it, with a UTF-16 fallback.

## Build and verification

The GitHub Actions workflow uses Java 17 and Gradle 8.9. It runs unit tests and assembles a **debug APK** on pushes to main/master, then uploads the GlassKeys-debug-apk artifact. The artifact is a ZIP containing the APK and is not itself an installable APK.

To build locally:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Output path:

app/build/outputs/apk/debug/app-debug.apk

## Install on Android

1. Download the artifact ZIP from a successful GitHub Actions run and extract it.
2. Install app-debug.apk on your Android device.
3. Open Glass Keys, tap **Klavyeyi etkinleştir**, enable it in system settings, then tap **Glass Keys'i seç** and choose it.

## Limitations

- The translucent gradients and strokes imitate a glass look; this is not full real-time iOS-style background blur.
- Suggestions are heuristic and based on a compact word list and local counts. Quality will not match a large on-device language model.
- The current automated tests focus on the typo-prefix matcher; they do not cover every UI path or device/editor combination.
- This project has been built and unit-tested in CI, but each release should still be tested on physical devices before everyday use.
- The generated APK is a debug build, not a production-signed Play Store release.
