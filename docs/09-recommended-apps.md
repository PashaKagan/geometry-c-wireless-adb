# 9. Recommended apps (links, not hosted)

This repo **does not host APKs**. Below are the apps that are useful on this unit and where to get
them. The head unit is **Android 5.1 (API 22)**, so many current apps won't install — on APKPure use
the **Versions / Old Versions** tab and pick the newest build that still lists **Android 5.0/5.1**
support. The unit has **no Play Store**, so sideload via ADB (`adb install app.apk`) or the USB
file-manager method; Play links below are just for identifying the app / grabbing it on your phone.

## Core

- **Open Headunit (Headunit Revived)** — the Android Auto receiver this unit uses, and it **works with
  the AAWireless dongle** on the Geometry C: plug the dongle into the **left** USB port, launch Open
  Headunit, and it auto-connects (hands-off, a few seconds after attach). The AAWireless idle-identity
  (`18D1:4EE1`) handling needed for this unit is in the app, so no manual USB-filter tweaks are
  required. Get it from the **releases page** —
  https://github.com/andreknieriem/open-headunit/releases — and it has an in-app **Check for Updates**
  to stay current. Open source and actively maintained.
- **Geely Remote** — the phone-side remote for this unit (root terminal, installs, diagnostics). See
  [doc 8](08-geely-remote-app.md); source + APK live in this project.

## File managers (for the USB-install method / root browsing)

- **MiXplorer** — excellent OTG + root file manager (freeware, not FOSS). Source: the official XDA
  thread / APKMirror. Works well on API 22.
- **Material Files** — FOSS, has a root toggle. Play: `me.zhanghai.android.files` · F-Droid.
- **Amaze File Manager** — FOSS (GPL), root mode. Play: `com.amaze.filemanager` · F-Droid.

## Utilities

- **Termux** — install the **F-Droid / GitHub** build (not the abandoned Play one). On your *phone*
  it gives `adb` (`pkg install android-tools`) to reach the unit.
- **MacroDroid** — for the regen auto-set macro. Play: `com.arlosoft.macrodroid` · APKPure (Versions
  tab for an API-22 build).
- **LocalSend** — easy Wi-Fi file transfer between phone and unit. `org.localsend.localsend_app`
  (check the Versions tab for an API-22-compatible build).

## Media / nav (sideload API-22 builds)

- **VLC** — `org.videolan.vlc`, still ships builds that run on 5.1 (APKPure Versions tab).
- Pick older APKPure builds for any player/launcher — anything post ~2023 often requires API 24+.

## The Settings shortcut

The lightweight "open full Android Settings" APK that circulates for this unit is from the Geely
Geometry C **4pda** thread (showtopic **1048081**, posted by *Sn0k* alongside the `ES_Install` USB
method). This repo doesn't redistribute it — grab it from that thread, or just open Settings over ADB:

```sh
adb shell am start -n com.android.settings/.Settings
```

> Version guidance here is deliberately "use the Versions tab and pick the last build that lists
> Android 5.0/5.1" rather than hard-coding numbers that go stale — API-22 compatibility is the thing
> to check for every app.
