# 3. Rooting without flashing the boot image

This unit can be given **persistent root** — root over wireless ADB, and `su` for allow-listed apps —
**without flashing boot/Magisk**, by reusing the [USB boot hook](02-usb-boot-hook.md). This is the
safest route on a locked, signed-boot MediaTek unit, because nothing in the boot chain is touched.

> Device-owner rooting of your own head unit. Everything below needs physical USB access to your own
> car. Offsets and sha1s are **for the documented build** — verify before using.

---

## Why not Magisk / KernelSU

- **Kernel 3.10.65**, no published source → KernelSU / KernelSU-Next are out.
- **Magisk** on this API level means ≤ 25.2, and it works by **patching and flashing `boot`**. Here
  `boot.img` is **signed** (a 256-byte signature is appended) and `lk` does **MTK secure-boot
  verification**, so a repacked boot is likely rejected → no Android → you lose the hook that would
  let you recover. Not worth the brick risk.
- `adb root` is unavailable (`adbd` was built without `ALLOW_ADBD_ROOT`).

Relevant props: `ro.build.type=user`, `test-keys`, `ro.secure=1`, `ro.debuggable=1`,
`ro.adb.secure=1`.

So instead of flashing, we modify files in `/system` (writable via the hook) and persist via
`install-recovery.sh`.

---

## Part A — Root ADB (patched `adbd`)

`adbd` is built to **drop to the `shell` user**. A tiny branch patch makes it **stay root**. We don't
overwrite the stock binary in place — we keep a patched copy and **bind-mount** it over the running
one, so it's trivially reversible and self-rolls-back.

**The patch (documented build — verify the sha1 first):**

```
file:        /sbin/adbd        (copy out, patch, keep as /system/bin/adbd_root)
patch:       4 bytes at vaddr 0x402d04  ->  unconditional branch  B 0x402de8
             (skips the setuid/drop-privs path so adbd keeps uid 0)
sha1 before: 9be1376f550d…
sha1 after:  0e51c10b7016…
```

**Apply + persist** (run from the hook, `/system` remounted rw):

```sh
# place the pre-patched binary
cp /mnt/media_rw/udisk1/adbd_root /system/bin/adbd_root
chmod 755 /system/bin/adbd_root

# rootadb.sh: bind the patched adbd over the live one, restart adbd, auto-rollback
cp /mnt/media_rw/udisk1/rootadb.sh /system/bin/rootadb.sh
chmod 755 /system/bin/rootadb.sh

# autostart on every boot
grep -q rootadb.sh /system/bin/install-recovery.sh || \
  echo '/system/bin/rootadb.sh &' >> /system/bin/install-recovery.sh
```

`rootadb.sh` logic (sketch):

```sh
#!/system/bin/sh
# kill switch
[ -f /data/local/tmp/rootadb.disable ] && exit 0
mount -o bind /system/bin/adbd_root /sbin/adbd
setprop ctl.restart adbd
# (optional) verify adbd came back; if not, unmount to roll back
```

Result: `adb shell` connects and `id` shows **uid 0**. Toggling wireless ADB in Developer Options
keeps root (the bind mount stays). Kill switch: create `/data/local/tmp/rootadb.disable` and reboot.

> **Persistence gotcha:** write those `install-recovery.sh` lines with **`echo`, never `printf`** —
> `printf` silently no-ops on this shell and your root vanishes on the next boot. (This exact bug
> cost a trip.)

---

## Part B — `su` for apps (a small su daemon)

Root ADB covers the ADB shell, but on-device apps (a root file manager, your own helper app) also
want `su`. Rather than a full SuperSU/Magisk, a minimal **su daemon** is enough:

- `/system/xbin/geelysu --daemon` — a long-running root daemon, started from `install-recovery.sh`.
- `/system/xbin/su` — the client apps call; it connects to the daemon over an **abstract socket**
  (`@geelysu`).
- Access control via **`SO_PEERCRED`** against an allow-list file (`/system/etc/geelysu.allow`):
  only listed package UIDs (plus uid 0/2000) get a root shell. Keep this list tight — it's your
  whole security model.
- Log to `/data/local/tmp/geelysu.log` for debugging.

Confirmed working when a root-capable file manager can browse `/data`.

This is deliberately described at the design level rather than shipped as a binary — build your own
small daemon or adapt an open one; the important parts are *abstract socket + SO_PEERCRED allow-list
+ started from `install-recovery.sh`*.

---

## What root actually buys you here

- Everything the USB stick does, now **over wireless ADB** — no more car trips per iteration
  (the stick is only needed again after a factory reset).
- Read/modify `/system`; read other apps' protected data; proper backups.
- Root file managers, hosts-based ad-blocking, boot scripts, app-data backup/restore.

## What root does **not** buy you

- **No DVR footage** (it's not on the Android side — see [DVR](06-dvr.md)).
- **No vehicle feature unlocks** (those are vehicle-config-gated, not Android — see
  [Community tweaks](07-community-tweaks.md)).
- **No OS upgrade** (hard-stuck on Android 5.1).

---

## Recovery

Keep a full [partition backup](05-partitions-and-backup.md). Because nothing in the boot chain was
modified, the usual recovery is: kill switch file, or re-run a hook stick that removes the
`install-recovery.sh` lines and the added binaries, or (worst case) a factory reset followed by
re-running the hook.
