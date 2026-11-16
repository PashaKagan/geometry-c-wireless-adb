# Geely Remote (Android phone app)

Phone app that controls the Geely Geometry C head unit over **wireless ADB** — no laptop needed, and
it uses the unit's root once it's set up. It speaks ADB straight from the phone via a **vendored,
patched [dadb](app/src/main/java/dadb/)** tuned for this unit's old Android-5 `adbd`.

See **[../../docs/08-geely-remote-app.md](../../docs/08-geely-remote-app.md)** for the full feature
list and background.

## Get the APK

CI builds this on every change and publishes it to this repo's **Releases** (`latest`):

```
https://github.com/PashaKagan/geometry-c-wireless-adb/releases/download/latest/geely-remote.apk
```

Sideload it (the head unit has no Play Store):

```sh
adb install -r geely-remote.apk
```

## Build it yourself

```sh
cd android/geely-remote
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

- AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7, JDK 17, compileSdk 34, minSdk 26.
- The debug build is signed with the committed **`app/geely.keystore`** (alias `geely`) so rebuilds
  update in place instead of forcing an uninstall. **Note:** that keystore + its password are in the
  source on purpose (reproducible in-place updates for a hobby app) — anyone can therefore sign an
  update with the same package name. If that matters to you, regenerate the keystore and keep it out
  of source.

## Layout

- `app/src/main/java/com/pashakagan/geelyremote/` — the app (MainActivity, TerminalActivity, …).
- `app/src/main/java/dadb/` — vendored dadb with Android-5 `adbd` fixes (ADB flow-control OKAY-wait,
  legacy `shell:` fallback, empty-features banner, old-API-safe calls).

> Originally developed as a standalone project; contributed here to keep the Geometry C tooling in one
> place for the community.
