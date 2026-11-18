# 10. Terminal recipes (final versions)

Every tweak in this repo can also be driven from a **root terminal** once you've run the stick once
(root + wireless ADB in place). This is the day-to-day way to re-apply or adjust things without
rebuilding a stick. These are the exact, final command blocks this unit actually runs.

**How to run these:** paste a block into the **Root Terminal** of the
[Geely Remote app](08-geely-remote-app.md) (runs as root on the unit over wireless ADB), or into
`adb shell` / a root `adb shell` from a PC. Each block is self-contained so it survives the terminal's
one-shot nature.

---

## ⚠️ Read before pasting

- **Device-owner only, entirely at your own risk.** `/system` edits can destabilise the unit; keep a
  [partition backup](05-partitions-and-backup.md) and your factory-reset steps handy.
- **This unit's quirks (they shape every block below):**
  - busybox is at `/system/bin/busybox` but has **no `base64` and no `nohup`** applet, so scripts are
    written with a heredoc (not base64) and kept alive with an in-script `trap '' HUP` (not nohup).
  - toolbox is minimal: **no `head`, `pidof`, `wc`, `printf`** (use `busybox` for those).
  - the shell **cannot write `/sdcard`** -> use `/data/local/tmp` for scratch.
  - **`/system` is read-only** until you remount it (shown where needed), and busybox `mount` won't do
    it; use the system `mount` exactly as written.
  - **avoid casual reboots** (the unit rebuilds its app cache and is slow to come back).
  - run **one self-contained block per round** (the app's terminal drops the connection if you
    navigate away mid-command).

---

## 1. Steering-wheel buttons (v6) — install permanently

Writes the final v6 daemon to `/system/bin/steering.sh`, autostarts it on every boot, and starts it
now. Safe to re-run (idempotent). This is the same thing the stick's wheel toggle does.

```sh
BB=/system/bin/busybox
# /system read-write (system mount, not busybox)
mount -o rw,remount -t ext4 /dev/block/platform/mtk-msdc.0/by-name/system /system
# stop any running steering daemon + clear flags
for p in $($BB ps|$BB grep steering|$BB grep -v grep|$BB awk '{print $1}'); do kill $p 2>/dev/null; done
rm -f /data/local/tmp/steering.scrub /data/local/tmp/steering.off
# write v6 (heredoc, no base64) to the permanent location
cat > /system/bin/steering.sh <<'EOF'
#!/system/bin/sh
trap '' HUP; export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin
K=/data/local/tmp/keys.log; FLAG=/data/local/tmp/steering.scrub; OFF=/data/local/tmp/steering.off; CUR=0; LONG=0
[ "$1" = now ] || sleep 20
[ -f "$OFF" ] && exit 0
rm -f "$FLAG" 2>/dev/null; echo "$(date) v6 start $$" >> $K 2>/dev/null; logcat -c 2>/dev/null
logcat -v time -s PopView:* 2>/dev/null | while read line; do [ -f "$OFF" ] && { rm -f "$FLAG"; exit 0; }; case "$line" in *"_keyCode = 163, Action = 1"*) CUR=163; LONG=0; rm -f "$FLAG" ;; *"_keyCode = 165, Action = 1"*) CUR=165; LONG=0; rm -f "$FLAG" ;; *"_keyCode = 267, Action = 1"*) CUR=267; LONG=0; rm -f "$FLAG" ;; *"_keyCode = 163, Action = 2"*) if [ "$CUR" = 163 ] && [ "$LONG" = 0 ]; then LONG=1; : > "$FLAG"; { n=0; while [ -f "$FLAG" ] && [ $n -lt 60 ]; do input keyevent 90; n=$((n+1)); sleep 1; done; } & fi ;; *"_keyCode = 165, Action = 2"*) if [ "$CUR" = 165 ] && [ "$LONG" = 0 ]; then LONG=1; : > "$FLAG"; { n=0; while [ -f "$FLAG" ] && [ $n -lt 60 ]; do input keyevent 89; n=$((n+1)); sleep 1; done; } & fi ;; *"_keyCode = 267, Action = 2"*) if [ "$CUR" = 267 ] && [ "$LONG" = 0 ]; then LONG=1; input keyevent 231; echo "$(date) ASSIST" >> $K; fi ;; *"_keyCode = 163, Action = 0"*) rm -f "$FLAG"; [ "$CUR" = 163 ] && [ "$LONG" = 0 ] && input keyevent 87; CUR=0; LONG=0 ;; *"_keyCode = 165, Action = 0"*) rm -f "$FLAG"; [ "$CUR" = 165 ] && [ "$LONG" = 0 ] && input keyevent 88; CUR=0; LONG=0 ;; *"_keyCode = 267, Action = 0"*) rm -f "$FLAG"; [ "$CUR" = 267 ] && [ "$LONG" = 0 ] && input keyevent 85; CUR=0; LONG=0 ;; esac; done
EOF
chmod 755 /system/bin/steering.sh
# autostart once, on every boot
$BB grep -q '/system/bin/steering.sh' /system/bin/install-recovery.sh 2>/dev/null || echo '/system/bin/steering.sh &' >> /system/bin/install-recovery.sh
# /system back to read-only
mount -o ro,remount -t ext4 /dev/block/platform/mtk-msdc.0/by-name/system /system
# start now (trap '' HUP keeps it alive; no nohup needed)
( /system/bin/sh /system/bin/steering.sh now </dev/null >/dev/null 2>&1 & )
$BB sleep 2
echo "installed bytes:"; $BB wc -c < /system/bin/steering.sh
echo "autostart:"; $BB grep steering /system/bin/install-recovery.sh
echo "running:"; $BB ps 2>/dev/null | $BB grep steering | $BB grep -v grep || echo NONE
echo DONE
```

Behaviour: **tap** 163/165/267 = next / prev / play-pause; **hold** forward/back = continuous
fast-forward / rewind (only in apps that honour `MEDIA_FAST_FORWARD`/`REWIND` — VLC and Netflix in AA
work; YouTube Music ignores seek for songs); **hold play** = the phone's voice assistant (Gemini) in
Android Auto. Details in [doc 4](04-wheel-buttons.md).

### Steering — stop / start / status

```sh
BB=/system/bin/busybox
# turn OFF (daemon exits on next event):   touch /data/local/tmp/steering.off
# turn ON again:
rm -f /data/local/tmp/steering.off
for p in $($BB ps|$BB grep steering|$BB grep -v grep|$BB awk '{print $1}'); do kill $p 2>/dev/null; done
( /system/bin/sh /system/bin/steering.sh now </dev/null >/dev/null 2>&1 & )
$BB sleep 2; echo "running:"; $BB ps 2>/dev/null | $BB grep steering | $BB grep -v grep || echo NONE
```

To **capture your own wheel keycodes** (different wheel/firmware), watch the log while pressing:

```sh
logcat -c; logcat -v time -s PopView:* KeyInput:*
# press each wheel button, note the keyCode / Action, Ctrl-C (or close) when done
```

---

## 2. Wireless ADB (persistent)

```sh
setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
stop adbd; start adbd
getprop | grep adb.tcp
```

After a cold boot wireless debugging may show off; re-enable in Developer Options or re-run the above.

---

## 3. Open the hidden stock Settings

```sh
am start -n com.android.settings/.Settings
```

---

## 4. Lighter UI animations

```sh
settings put global window_animation_scale 0.5
settings put global transition_animation_scale 0.5
settings put global animator_duration_scale 0.5
# 0 = off, 1 = stock
```

---

## 5. Apps — install / list / disable / uninstall

```sh
pm install -r /data/local/tmp/app.apk        # install (push the apk there first)
pm list packages | grep -i <name>            # find a package
am start -n <pkg>/<activity>                  # launch
```

**Disable an app without uninstalling** (how to park Open Headunit while testing another AA build —
reversible, keeps your data):

```sh
pm disable-user --user 0 <pkg>               # e.g. the OH package
pm enable <pkg>                              # re-enable later
```

> ⚠️ Disabling or uninstalling **system** apps can break head-unit features. Only touch packages you
> recognise, and prefer `disable-user` (reversible) over `uninstall`.

---

## 6. Launch the voice assistant (Gemini in AA)

```sh
input keyevent 231    # KEYCODE_VOICE_ASSIST (what hold-play uses)
```

---

## 7. Seat-preset fix — ⚠️ risky / unverified

```sh
pm clear com.neusoft.optimus.wheeljack.setting
# then reboot for it to take effect:
# reboot
```

> ⚠️ **Use only if the seat preset is already corrupted.** `pm clear` wipes that app's entire state,
> and a community report says it **may clear ALL of your user settings**, not just the seat preset.
> It is **not verified**. Expect to redo other settings. See [doc 7](07-community-tweaks.md).

---

## 8. Root ADB (patched adbd) — reference

The patched-`adbd` + `su` daemon install is in [doc 3](03-rooting.md). The binaries are
**build-specific** (the `adbd` patch offset is for one firmware), so this isn't a copy-paste block for
every unit — rebuild per that doc. Once installed, apply/restore:

```sh
/system/bin/rootadb.sh                                   # bind the patched adbd over the live one
# kill switch: touch /data/local/tmp/rootadb.disable  (then reboot)
id                                                       # expect uid=0(root) over ADB
```

---

## 9. Partition backup (read-only, can't brick)

Full `dd` backups are meant to run from the [USB hook](05-partitions-and-backup.md) (writes to the
stick). From a terminal you can at least read the map:

```sh
ls -l /dev/block/platform/mtk-msdc.0/by-name/      # partition -> block device map
cat /proc/partitions
```

> Only ever `dd` **from** a partition (read). Never `dd` **to** `by-name/*` unless you know exactly
> what you're doing — that's how units get bricked.

---

## 10. Diagnostics

```sh
dumpsys activity activities | grep -i mResumedActivity   # foreground app
logcat -v time -s PopView:* KeyInput:*                   # wheel key events
getprop | grep -iE 'build|product|secure'                # unit identity
```

The Geely Remote app's **Get report** button bundles most of these. When sharing logs publicly,
**strip navigation / address lines** (e.g. `AapNavigation`) first.
