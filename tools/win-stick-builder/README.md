# Geely Stick Builder (Windows)

A small Windows GUI that **detects a USB drive, formats it FAT32, and writes a boot-hook stick**
that applies the features you tick — so re-applying everything after a factory reset is a few clicks
instead of hand-assembling scripts.

> Formatting **erases** the chosen drive. Device-owner tooling, no warranty — read the repo
> [disclaimer](../../README.md) and [the hook doc](../../docs/02-usb-boot-hook.md) first.

## Download

Grab the packaged zip from the repo's Releases (built by CI, no binaries bundled):
**https://github.com/PashaKagan/geometry-c-wireless-adb/releases/download/stick-builder/GeelyStickBuilder.zip**

## Run it

1. Put your own kit files in [`assets/`](assets/) (see that folder's README — binaries/APKs are
   never in this repo; you supply them). Toggles whose asset is missing stay greyed out.
2. Double-click **`Run-GeelyStickBuilder.cmd`** — it elevates (formatting needs admin) and bypasses
   execution policy for just this script.
3. Pick the USB drive, tick features, **Format + Build Stick**.
4. Safely eject, insert in the **left port** with the car on. It applies everything in **one pass,
   no reboot**. Check `DONE.txt` / `done.log` on the stick afterwards.

Only **removable USB** drives are listed, and the system drive is excluded — but **double-check the
letter and size** in the confirm dialog before you hit yes. Use a small (≤ 32 GB) FAT32 stick.

## Features

| Toggle | What it writes | Asset needed |
|---|---|---|
| Wireless ADB (5555, persistent) | props + starts adbd | — |
| Persistent root (patched adbd + su daemon) | `/system` + install-recovery | your kit binaries + `rootadb.sh` |
| Steering-wheel fix | `/system/bin/steering.sh` + autostart | `steering.sh` (shipped) |
| Settings shortcut app | `pm install` | `Settings_1.0.apk` (you supply) |
| Set animation scale | 3 global settings | — |
| Seat-preset fix | runs `assets/seatfix.sh` | `seatfix.sh` (you supply) |
| Read-only partition backup | `dd` to the stick (can't brick) | — |
| Extra APKs | `pm install assets/apps/*.apk` | your APKs |

The generated `done.sh` honours the unit's quirks automatically: single pass (the hook runs once per
boot), `echo`-only persistence, the correct `/system` remount, no `printf`, and **no reboot**.

## Notes

- It stashes a backup copy of the trigger folder in `_trigger_backup\` on the stick (the firmware
  deletes the live one after it runs), so you can re-insert later without rebuilding.
- Pure-PowerShell + WinForms — no install, no build step. Works on Windows 10/11.
- Not yet run end-to-end on hardware at the time of writing; review the generated `done.sh` on the
  stick before inserting if you want to be cautious.
