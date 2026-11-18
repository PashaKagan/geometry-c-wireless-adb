# Geely Geometry C (ECARX / Android 5.1) — owner tinkering notes

Owner notes on the **Geely Geometry C**'s ECARX head unit: how to enable **wireless ADB**,
how to get a **persistent root shell without flashing the boot image**, how to make the
**steering-wheel media buttons work** in projection apps, how to **back up the partitions**,
and an honest list of what turned out to be possible vs. not (DVR footage, vehicle feature
toggles, etc.).

Everything here was done on a device **I own**, reusing the head unit's *own* factory USB
install hook. It is not an exploit against anyone else's property, it needs physical access to
your own car, and most of it is reproducible on the same ECARX/MediaTek "alps E01" platform.

> Research, reverse-engineering and tooling in this repo were done with the help of
> **Claude (Anthropic)**.

---

## ⚠️ Read this first — disclaimer

- **Do all of this entirely at your own risk.** You are solely responsible for anything that happens.
- It may **void your vehicle / infotainment warranty.**
- In the worst case you could **destabilise or soft-brick the head unit.** Keep your factory-reset
  procedure handy, and read the backup doc before touching `/system`.
- **No support is provided.** Issues and PRs may be ignored or closed unread.
- Comply with your local laws. **Never interact with the screen while driving**, and never use any of
  this to defeat vehicle safety features.
- No affiliation with Geely, ECARX, MediaTek or Google.

---

## The unit (confirmed on)

| | |
|---|---|
| Vehicle | Geely Geometry C (2022) |
| Head unit | ECARX / MediaTek **MT6735**, "alps" **E01**-class (product `GE13JQJ`, NEUSOFT/Bird ODM) |
| OS | Android **5.1** (API 22), kernel 3.10.65 |
| Build family | `SWGE13J…` (`ro.build.characteristics=carcorder`) |
| SELinux | **Disabled** |

Other Geely/ECARX units and model years may differ — treat offsets, package names and partition
numbers as *this build's*, and verify before relying on them.

---

## Contents

1. **[Wireless ADB](docs/01-wireless-adb.md)** — enable shell-level ADB over Wi‑Fi via the USB hook; connect from a laptop or just a phone.
2. **[The USB boot hook](docs/02-usb-boot-hook.md)** — how the firmware's privileged auto-run works, and the constraints that bite you (once-per-boot, no `printf`, no reboot, `/system` remount).
3. **[Rooting without flashing](docs/03-rooting.md)** — persistent **root ADB** + a small **su daemon**, no boot-image flash (Magisk ruled out and why).
4. **[Steering-wheel buttons](docs/04-wheel-buttons.md)** — making next/prev/play work in Android Auto / Open Headunit (the earlier "impossible" verdict was wrong).
5. **[Partitions & backup](docs/05-partitions-and-backup.md)** — full partition map and a read-only `dd` backup you run from the hook.
6. **[DVR / dashcam footage](docs/06-dvr.md)** — why the clips aren't on the Android side, and the only real ways to get them.
7. **[Community tweaks](docs/07-community-tweaks.md)** — Settings shortcut, engineering-menu audio warning, firmware-swap caveat, OBD2, regen macro.
8. **[Geely Remote phone app](docs/08-geely-remote-app.md)** — the companion Android app that drives the unit over wireless ADB.
9. **[Recommended apps](docs/09-recommended-apps.md)** — where to get the useful apps (APKPure / Play / source), with Android-5.1 version guidance.
10. **[Terminal recipes](docs/10-terminal-recipes.md)** — final copy-paste command blocks for every fix (steering v6, wireless ADB, animations, Settings, apps, seat fix, diagnostics) once the stick has been run once.

## Tools & apps in this repo

- **[Geely Remote](android/geely-remote/)** — the phone app (source here; APK built by CI to
  [Releases](../../releases)). Controls the unit over wireless ADB, with a root terminal and
  diagnostics.
- **[Windows USB-stick builder](tools/win-stick-builder/)** — a GUI that detects a USB drive, formats
  it, and writes a boot-hook stick from the features you tick (wireless ADB, root, wheel fix, Settings,
  animations, backup, …). **[Download the zip](https://github.com/PashaKagan/geometry-c-wireless-adb/releases/download/stick-builder/GeelyStickBuilder.zip)** (built by CI; bundles the author's own kit — `adbd_root` is build-specific; only third-party APKs are left out). See also **[ROADMAP.md](ROADMAP.md)**.
- **Android Auto:** this unit runs **Open Headunit** with the **AAWireless** dongle — see
  [docs/09](docs/09-recommended-apps.md).

---

## TL;DR of what's possible

- ✅ **Wireless ADB** (shell) over the unit's own USB install hook.
- ✅ **Persistent root** — patched `adbd` (root ADB) + a small `su` daemon, **no boot flash**, survives reboots.
- ✅ **Steering-wheel media buttons** in projection apps, with **tap vs hold**: tap = next/prev/play-pause, hold fwd/back = continuous fast-forward/rewind (in apps that honour seek, e.g. VLC/Netflix in AA), hold play = the phone's voice assistant (Gemini) in AA. See [doc 4](docs/04-wheel-buttons.md).
- ✅ **Full partition backup** (read-only `dd` to the stick), and a confirmed partition map.
- ✅ **Android Auto** via **Open Headunit + the AAWireless dongle** (works on this unit — see [docs/09](docs/09-recommended-apps.md)).
- ✅ A **phone app** ([Geely Remote](android/geely-remote/)) to drive the unit over wireless ADB with a root terminal.
- ✅ Install blocked apps, open the hidden stock **Settings**, trim animations, back up/move files over Wi‑Fi.
- ❌ **DVR footage** is on the camera module's own card, not the Android filesystem — not reachable in software.
- ❌ **Vehicle feature toggles** (ADAS/HUD/CarPlay/etc.) are **vehicle-config-gated**, not Android-gated — can't be "unlocked" from the head unit.
- ❌ **No OS upgrade** — hard-stuck on Android 5.1; no newer AOSP exists for this board.

---

*Posted by an owner, for owners. Use responsibly.*
