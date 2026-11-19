# 8. Geely Remote — Android phone app

A companion **Android phone app** that controls this head unit over **wireless ADB**, with no laptop
and (now) using the unit's root. It speaks ADB directly from the phone via a vendored, patched
**dadb** (pure-JVM ADB) tuned for this unit's old Android-5 `adbd`, so you don't need Termux or a PC.

- **Source:** in this repo under [`android/geely-remote/`](../android/geely-remote/) (contributed to
  keep the Geometry C tooling in one place).
- **APK (stable):** built by this repo's CI and published to Releases —
  `https://github.com/PashaKagan/geometry-c-wireless-adb/releases/download/latest/geely-remote.apk`
  (sideload with `adb install -r geely-remote.apk`).

## Feature set

- **Connect** to `ip[:port]` with auto-reconnect + retry, and **Scan** to find the head unit on the
  network.
- **Root Terminal** — paste a command, run it **as root** on the unit, and **Copy** the output
  (handy for remote diagnostics). Plus a one-tap **Get report**.
- **App management** — list / launch / uninstall apps (including system apps), **install APK(s)**.
- **Quick tweaks** — fast (light) animations, **seat-preset fix**, **ADB-persistence test**.
- **Diagnostics** — capture a crash log, pull the newest Open Headunit logs, take a screenshot;
  screen **recording** is done by pulling `screencap` frames and encoding them on the phone (the
  unit's hardware screen-record is unusable).
- **Log card** — copy all / save / clear, with timestamps. Files the app saves go to the phone's
  `Download/GeelyRemote/<type>/`.

> Steering-wheel buttons are handled by the native system daemon (see [doc 4](04-wheel-buttons.md)),
> not by this app. The app's role there is just the terminal you use to install or tweak that daemon.

## Why a custom app (and not just `adb`)

The unit runs **Android 5.1** with an old `adbd` that breaks stock ADB libraries. The app vendors
**dadb** with fixes for exactly these quirks (ADB flow-control OKAY-wait after each write, legacy
`shell:` fallback, empty-features banner, old-API-safe calls). See the repo for details. The net
effect: reliable push/install/pull and a root shell straight from the phone.

## Relationship to this repo

This repo is the **knowledge hub** (how the unit works, rooting, tweaks, the USB stick builder).
The phone app is the **day-to-day remote** once root + wireless ADB are in place — at which point the
USB stick is only needed again after a factory reset.
