# 2. The USB boot hook (privileged auto-run)

Everything in this repo is built on one thing: the head unit's firmware will **run a script from a
USB stick as root**. This is the factory/service install path (the "ES_Install" USB method that
circulates in Geely owner communities). Understanding its exact behaviour saves a lot of wasted
car trips.

---

## What it is

On insert, the firmware scans the stick for a specifically-named folder containing a specifically-
named inner script, and if present **executes it at elevated privilege**. The inner script simply
sources a `done.sh` you place at the stick root, so in practice **`done.sh` runs as root**.

```
<USB root>/
├── done.sh                                                  # your payload (runs as root)
└── b832bc61472727635baffcf25dd28e9f239273e2/
    └── d4ad0e797c738a44a9dd1b9231d201e8374dfda2.sh          # the firmware's entry point
```

The inner script is tiny and fixed:

```sh
#!/system/bin/sh
chmod +x /mnt/media_rw/udisk1/done.sh
SCRIPT_PATH="/mnt/media_rw/udisk1/done.sh"
. "$SCRIPT_PATH"
```

Confirmed privilege level inside `done.sh`:

```
uid=0(root) gid=0(root) context=kernel
```

Full root, SELinux Disabled. That's the entire basis for rooting this unit without touching the
bootloader — you already have a root code-execution primitive.

---

## The constraints that actually bite

These were all learned the hard way; respect them or you'll burn car trips:

1. **It runs ONCE per boot.** The watcher loops until it finds the trigger folder, runs it, then
   exits. A **second stick insert in the same boot does nothing.** Collect any results on the *next*
   boot's insert, or over ADB / a root file manager — not with a two-insert design.

2. **No reboot inside hook scripts (as a rule).** Rebooting this unit rebuilds the app cache and is
   slow. Start daemons live instead. (The one place a reboot is sometimes used is the very first
   wireless-ADB enable, and even that isn't required.)

3. **The shell is minimal.** `/system/bin/sh` here has **no `printf`**, no `head`, `pidof`, `wc`.
   Using `printf` fails *silently* — a classic way to think you wrote a boot-persistence line when
   you didn't. **Use `echo`.** busybox `setsid` also fails; detach with
   `sh -c 'trap "" 1; exec <cmd> </dev/null >log 2>&1 &'`.

4. **`/system` is mounted read-only** and must be remounted with the **system** `mount`, not busybox:
   ```sh
   mount -o rw,remount -t ext4 /dev/block/platform/mtk-msdc.0/by-name/system /system
   ```
   (busybox's remount appears to succeed but leaves it read-only.)

5. **Backlight-flicker feedback doesn't work** as a "script alive" signal. A reliable visible signal
   is launching an app, e.g. `am start -n com.android.settings/.Settings`.

6. **Stick must be small FAT32** (≤ 32 GB worked; 128 GB didn't trigger) in the **left port**
   (`/mnt/media_rw/udisk1`).

---

## Boot persistence

To make something run on **every** boot (not just on stick insert), append it to the init-run
script `/system/bin/install-recovery.sh` (started by the init `flash_recovery` service: root,
oneshot, no early-exit). Write the lines with `echo`, and remount `/system` rw first (see above).

Example (adds an autostart line for a daemon you installed to `/system/bin`):

```sh
mount -o rw,remount -t ext4 /dev/block/platform/mtk-msdc.0/by-name/system /system
grep -q my-daemon.sh /system/bin/install-recovery.sh || \
  echo '/system/bin/my-daemon.sh &' >> /system/bin/install-recovery.sh
mount -o ro,remount -t ext4 /dev/block/platform/mtk-msdc.0/by-name/system /system
sync
```

---

## Safety

Before you write anything to `/system`, read [Partitions & backup](05-partitions-and-backup.md) and
take a backup. The hook can also run a purely **read-only** backup (dd partitions to the stick) —
that can't brick anything and gives you a restore point.
