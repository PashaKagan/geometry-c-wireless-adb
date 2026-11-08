# Wireless ADB on the Geely Geometry C (ECARX / Android 5.1 head unit)

Owner notes on enabling **wireless ADB (shell-level)** on the Geely Geometry C's ECARX head
unit using the unit's *own* USB install hook — what it does, how to connect afterwards (from a
laptop **or** just a phone), what you can (and can't) do with it, and a set of findings from
digging into common owner requests.

> This documents a procedure on a device **you own**, using the head unit's existing factory
> install mechanism. It is not an exploit against anyone else's property, requires physical
> access to your own car, and yields an ordinary (non-root) ADB shell.

---

## ⚠️ Read this first — disclaimer

- **Do this entirely at your own risk.** You are solely responsible for anything that happens.
- It may **void your vehicle/infotainment warranty.**
- In the worst case you could **destabilise or soft-brick the head unit.** Have your factory-reset
  procedure handy.
- **No support is provided. None whatsoever.** Issues and PRs may be ignored or closed unread.
- Comply with your local laws. **Never interact with the screen while driving**, and don't use
  this to defeat vehicle safety features.
- No affiliation with Geely, ECARX, MediaTek or Google.

---

## Scope / confirmed on

| | |
|---|---|
| Vehicle | Geely Geometry C (2022) |
| Head unit | ECARX / MediaTek ("alps" E01-class) |
| OS | Android **5.1** (API 22) |
| Root | **None** (no `su`; SELinux reports *Disabled* but there is no root path) |
| ADB user | `shell` (uid 2000) |

Other Geely/ECARX units and model years may differ — the mechanism may or may not apply.

---

## Network: get both on the same Wi-Fi

Your ADB client (laptop or phone) and the head unit must be on the **same network**. Easiest options:

- **Personal / phone hotspot:** turn on your phone's hotspot and connect the **head unit's Wi-Fi**
  to it. Now the phone is already on that network, and a laptop joined to the same hotspot can reach
  the unit too. (This is the usual setup — the unit lives on your phone's hotspot.)
- **Shared Wi-Fi:** put the head unit and your laptop/phone on the same home or router Wi-Fi.

You'll need the head unit's IP on that network (hotspot client list, the unit's Wi-Fi details, or a
tool like LocalSend running on the unit).

---

## How it works (short version)

The head unit's firmware runs a **privileged auto-run hook** from a USB stick: if a
specifically-named folder and a `done.sh` exist at the stick root, it executes `done.sh` at
elevated privilege when the stick is inserted. This is the unit's own factory/service install
path (the "ES_Install" USB method that circulates in Geely owner communities). We simply reuse
it to switch on wireless ADB.

Result: `adbd` listening on **TCP 5555**, reachable from any device on the same network. The
resulting shell is the **`shell` user, not root.**

---

## What you need

- A **small USB stick, formatted FAT32.** This matters: a large/high-capacity stick (e.g. 128 GB)
  **failed** to trigger the hook here; a small **≤ 32 GB FAT32** stick worked.
- Use the **left USB port** (the hook reads from `/mnt/media_rw/udisk1`).
- An ADB client on the same Wi-Fi/hotspot as the head unit — a computer with **platform-tools**,
  **or just an Android phone** (see *Doing it from a phone* below).

---

## The enabler

Lay the stick out like this (exact names — the firmware looks for them):

```
<USB root>/
├── done.sh
└── b832bc61472727635baffcf25dd28e9f239273e2/
    └── d4ad0e797c738a44a9dd1b9231d201e8374dfda2.sh
```

**`done.sh`** (the fixed script):

```sh
#!/system/bin/sh
# Enable wireless ADB on TCP 5555 via the privileged boot hook, then reboot.
settings put global adb_enabled 1 2>/dev/null
setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
stop adbd 2>/dev/null
start adbd 2>/dev/null
setprop ctl.restart adbd 2>/dev/null
# remove the hook folder so it doesn't run again, then reboot to apply
rm -rf /mnt/media_rw/udisk1/b832bc61472727635baffcf25dd28e9f239273e2
sync
sleep 8
reboot
```

**`b832…/d4ad….sh`** (the inner launcher that the firmware actually invokes):

```sh
#!/system/bin/sh
chmod +x /mnt/media_rw/udisk1/done.sh
SCRIPT_PATH="/mnt/media_rw/udisk1/done.sh"
. "$SCRIPT_PATH"
```

Notes:
- `settings put` can hang at very early boot on some units — it's wrapped and non-fatal.
- `persist.adb.tcp.port` keeps the port set across reboots, **but** the ROM's wireless-debugging
  UI toggle still tends to revert after a cold boot (see *Reconnecting* below).
- The `rm` line deletes the hook folder after it runs. **Keep a backup copy** of the folder so you
  can re-run it later.

---

## Step by step

1. Format a small USB stick as **FAT32**.
2. Put `done.sh` at the root and create the `b832…` folder with the inner `.sh` inside.
3. **Car on.** Insert the stick into the **left USB port**.
4. Wait — the script reboots the unit to apply.
5. Find the head unit's IP (your phone's hotspot client list, or the car's Wi-Fi details; a tool
   like LocalSend running on the unit will show it too).
6. From your ADB client:
   ```sh
   adb connect <headunit-ip>:5555
   adb devices          # accept any on-screen auth prompt
   ```

---

## Doing it from a phone (no laptop)

You don't need a computer — the head unit usually joins **your phone's hotspot**, so the phone and
the unit are already on the same network, which is ideal.

**Option A — Termux (no root on the phone needed):**
```sh
pkg install android-tools
adb connect <headunit-ip>:5555
adb devices
adb shell                       # or: adb install app.apk, adb push/pull, etc.
```

**Option B — a remote-ADB client app:** apps such as *Remote ADB Shell* connect straight to the
head unit's `adbd` over TCP (`<headunit-ip>:5555`) for a quick shell without installing Termux.

Notes for the phone route:
- File transfers work via `adb push/pull` in Termux; for drag-and-drop file moves, an app like
  **LocalSend** on both ends is often easier.
- **Screen mirroring (scrcpy) is desktop-only** — from a phone you're limited to shell, app
  installs and file operations, not mirroring/controlling the unit's screen.

---

## Reconnecting (after a reboot)

The TCP port property persists, but the unit may show wireless debugging as **off** after a cold
boot. Re-enable it in **Settings → Developer Options → Wireless ADB** (no stick needed again), then
`adb connect <headunit-ip>:5555`.

> On some trims the stock Android **Settings** app is hidden from the launcher. Once ADB is up you
> can open it with:
> ```sh
> adb shell am start -n com.android.settings/.Settings
> ```

---

## Example commands

```sh
adb devices
adb shell                                   # interactive shell (non-root)
adb install app.apk                         # sideload an app
adb push file /sdcard/Download/             # copy to the unit
adb pull /sdcard/Download/file ./           # copy from the unit
adb shell pm list packages                  # list installed packages
adb shell dumpsys activity activities       # inspect foreground app, etc.

# lighter UI animations
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

---

## Tools & apps you can connect

- **scrcpy** (desktop) — mirror and control the head-unit screen from your PC over Wi-Fi:
  `scrcpy -s <headunit-ip>:5555`. Great for setup, screenshots, or poking at hidden screens.
- **Any file transfer** over `adb push/pull`, or an on-device file manager you sideload.
- **Sideloaded apps** — e.g. an Android Auto receiver, media players, FOSS apps, a shortcut to the
  stock Settings.
- **ADB-driven automation** / scripted setup after a reset.

## What it's good for

- Install apps the stock launcher blocks.
- Mirror/control the screen for configuration without reaching across the dash.
- Trim UI animations / adjust density (within limits).
- Inspect the system (packages, services, props) when troubleshooting.
- Back up and move files over Wi-Fi instead of juggling USB sticks.

---

## Limitations & findings (the honest part)

Things people often ask about this unit, and what actually turned out to be possible:

- **No root.** `su` isn't present; SELinux is *Disabled* but there's still no root path. ADB is the
  `shell` user. So anything needing root — removing system/bloat/ad packages, writing to `/system`,
  backing up another app's protected data — is **not** possible by this method.
- **The wireless-ADB toggle reverts on reboot** — re-enable it in Developer Options (above).
- **Steering-wheel next/previous (media) buttons can't be remapped** for projection apps. On this
  unit they travel over the car's MCU serial bus straight to **Bluetooth AVRCP** and never enter
  Android's input / MediaSession layer — so there's literally no key event for a remapper app to
  catch.
- **Dashcam / DVR footage is not reachable from any Android file browser.** The DVR microSD is wired
  to the **camera module, not the Android SoC** — there's no second block device and nothing mounts
  even with the DVR app open, and the DVR app has no file-share/export. The only no-card route is
  screen-recording playback.
- **Vehicle feature toggles are BCM-config-gated, not Android-gated.** The settings app ships toggles
  for the whole model line (various ADAS features, HUD, wireless charge, CarPlay, etc.), but which
  appear on *your* car is decided by the body-control-module configuration read over the vehicle bus.
  Features your trim's config doesn't enable won't show and **can't be "unlocked" from the head
  unit** in software — e.g. CarPlay is compiled into the firmware but config-gated off on some trims.
- **Seat-comfort preset corruption:** a *targeted* `pm clear` of the vehicle-settings app **may**
  restore it without a full factory reset — documented as a theory worth trying, **not verified.**
- **Factory engineering/test menu exists** but it's a calibration/flashing tool — **leave it alone.**

---

*Posted by an owner, for owners. Use responsibly.*
