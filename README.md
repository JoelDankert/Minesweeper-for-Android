# Minesweeper for Android

A native Android Minesweeper game focused on configurable play modes and a clean offline experience.

## Features

- Classic Minesweeper gameplay with reveal, flagging, cording, timer, and saved progress.
- Custom game modes with configurable width, height, mine count, no-guess mode, and no-flag mode.
- Recent games and per-mode score history.
- Adjustable controls, animation speed, vibration, screen shake, and board display options.
- Light and dark themes with multiple color palettes.
- No network permission, ads, analytics, billing, or account requirement.

## Build

This project builds with the included Gradle wrapper:

```sh
./gradlew :app:assembleRelease
```

The release APK is written to:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Versioning

The Android package is `com.joeld.minesweeper`.

The current release version is configured in [app/build.gradle.kts](app/build.gradle.kts):

- `versionCode = 2`
- `versionName = "1.1"`

For an F-Droid submission, create a matching git tag for the release, for example:

```sh
git tag v1.1
git push origin v1.1
```

## F-Droid

The repository includes store listing text under `fastlane/metadata/android/en-US/` and a draft fdroiddata metadata file under `fdroid/com.joeld.minesweeper.yml`.

For the official F-Droid repository, copy `fdroid/com.joeld.minesweeper.yml` to `metadata/com.joeld.minesweeper.yml` in an `fdroiddata` merge request after tagging the matching release.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
