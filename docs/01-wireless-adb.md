# 1. Wireless ADB (shell-level)

Enable `adbd` on TCP **5555** using the head unit's own USB install hook, then connect from a
laptop or just a phone. This gives an ordinary **`shell` user (uid 2000)** ADB shell — see
[Rooting](03-rooting.md) to make that shell (and apps) run as root.

> For how the hook works and its gotchas, read [The USB boot hook](02-usb-boot-hook.md).

---

## What you need

- A **small USB stick, formatted FAT32.** This matters: a large stick (128 GB) **failed** to
  trigger the hook here; a small **≤ 32 GB FAT32** stick worked.
- The **left USB port** (the hook reads from `/mnt/media_rw/udisk1`). Note the left port is also the
  only one the AAWireless dongle works in, so the stick and Android Auto can't be plugged at once.
- An ADB client on the **same Wi‑Fi/hotspot** as the unit — a PC with platform-tools, or just a phone.

---

## The stick layout

Exact names — the firmware looks for them:

```
<USB root>/
├── done.sh
└── b832bc61472727635baffcf25dd28e9f239273e2/
    └── d4ad0e797c738a44a9dd1b9231d201e8374dfda2.sh
```

**`b832…/d4ad….sh`** (the inner launcher the firmware actually invokes):

```sh
#!/system/bin/sh
chmod +x /mnt/media_rw/udisk1/done.sh
SCRIPT_PATH="/mnt/media_rw/udisk1/done.sh"
. "$SCRIPT_PATH"
```

**`done.sh`** (enable wireless ADB):

```sh
#!/system/bin/sh
# Enable wireless ADB on TCP 5555 via the privileged boot hook.
settings put global adb_enabled 1 2>/dev/null
setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
stop adbd 2>/dev/null
start adbd 2>/dev/null
setprop ctl.restart adbd 2>/dev/null
# remove the hook folder so it doesn't run again
rm -rf /mnt/media_rw/udisk1/b832bc61472727635baffcf25dd28e9f239273e2
sync
```

Notes:
- `persist.adb.tcp.port=5555` **survives cold boot**, so the port stays set. The ROM's
  wireless-debugging *UI toggle* may still show off after a cold boot — just re-enable it in
  Developer Options (no stick needed).
- The original circulating scripts end with `sleep 8; reboot`. A reboot is **not required** to start
  `adbd` (the `stop/start` above does it live) and reboots on this unit are slow (they rebuild the
  app cache), so it's left out here. Add it back only if your unit won't start `adbd` live.
- Keep a **backup copy** of the `b832…` folder — the script deletes it after running so it doesn't
  re-fire, and you'll want it again after a factory reset.

---

## Step by step

1. Format a small USB stick **FAT32**.
2. Put `done.sh` at the root and create the `b832…` folder with the inner `.sh` inside.
3. **Car on.** Insert the stick into the **left USB port**. Give it ~30–60 s.
4. Find the head unit's IP (your phone's hotspot client list, the car's Wi‑Fi details, or a tool like
   LocalSend running on the unit).
5. From your ADB client:
   ```sh
   adb connect <headunit-ip>:5555
   adb devices          # accept any on-screen auth prompt
   ```

---

## Network: same Wi‑Fi

Client and head unit must be on the **same network**:

- **Phone hotspot (usual):** connect the head unit's Wi‑Fi to your phone's hotspot. The phone is
  already on it; a laptop on the same hotspot can reach the unit too.
- **Shared Wi‑Fi:** put both on the same home/router network.

---

## Doing it from a phone (no laptop)

The unit usually joins **your phone's hotspot**, so phone and unit are already on the same network.

- **Termux (no phone root needed):**
  ```sh
  pkg install android-tools
  adb connect <headunit-ip>:5555
  adb shell
  ```
- **A remote-ADB client app** (e.g. *Remote ADB Shell*) connects straight to `<headunit-ip>:5555`.
- File moves: `adb push/pull` in Termux, or LocalSend on both ends for drag-and-drop.
- **scrcpy screen mirroring is desktop-only** — from a phone you get shell, installs and file ops,
  not mirroring.

---

## Reconnecting after a reboot

The TCP port persists, but wireless debugging may show **off** after a cold boot. Re-enable it in
**Settings → Developer Options**, then `adb connect`. If the stock Settings app is hidden from the
launcher:

```sh
adb shell am start -n com.android.settings/.Settings
```

---

## Useful non-root commands

```sh
adb install app.apk
adb shell pm list packages
adb shell dumpsys activity activities         # foreground app
# lighter UI animations:
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

A `shell`-user limitation worth knowing: the ADB shell **cannot write `/sdcard`** on this unit —
use `/data/local/tmp` for scratch files. The toolbox is minimal (no `head`, `pidof`, `wc`,
`printf`); parse output on the client side.

For the full set of **final, copy-paste command blocks** — steering v6, persistent wireless ADB,
animations, Settings, app install/disable, the seat fix, root recipes and diagnostics — see
**[doc 10: Terminal recipes](10-terminal-recipes.md)**.
