# 5. Partitions & backup

Before writing anything to `/system`, take a backup. The [USB hook](02-usb-boot-hook.md) runs as
root, so you can `dd` partitions straight to the stick — a **read-only** operation on the unit that
can't brick anything and gives you a restore point.

---

## Partition map (documented build)

Storage is internal eMMC `mmcblk0`; by-name links live under
`/dev/block/platform/mtk-msdc.0/by-name/`.

| name | dev | approx size | notes |
|---|---|---|---|
| preloader | `mmcblk0boot0` / `boot1` | 4 MB each | hardware boot partitions (not a by-name entry) |
| proinfo | p1 | 3 MB | |
| nvram | p2 | 5 MB | calibration (BT/Wi‑Fi MAC) |
| protect1 / protect2 | p3 / p4 | 10 MB | `/protect_f`, `/protect_s` |
| lk | p5 | 0.5 MB | bootloader (secure-boot verify) |
| para | p6 | 0.5 MB | |
| **boot** | p7 | 16 MB | kernel+ramdisk, **signed** (256-byte sig appended) |
| recovery | p8 | 16 MB | |
| logo | p9 | 8 MB | |
| expdb | p10 | 10 MB | |
| seccfg | p11 | 0.5 MB | |
| oemkeystore | p12 | 2 MB | |
| secro | p13 | 6 MB | |
| keystore | p14 | 8 MB | |
| tee1 / tee2 | p15 / p16 | 5 MB | |
| frp | p17 | 1 MB | |
| nvdata | p18 | 32 MB | |
| metadata | p19 | 38 MB | |
| system | p20 | ~4 GB | ext4, mounted ro |
| cache | p21 | ~400 MB | |
| userdata | p22 | ~8.7 GB | `/data` |
| ivres | p23 | ~800 MB | voice/IVR resources |
| **resources** | p24 | **~15 GB** | UI assets — **never dump this**; it will fill the stick |
| flashinfo | p25 | 16 MB | |

---

## Read-only backup from the hook

`done.sh` that backs up the small/critical partitions to the stick. Key safety points learned the
hard way: this unit has **no `blockdev`/`readlink -f`** to measure partition size, so do **not** try
to auto-detect and copy "all small" partitions — an unmeasured giant (`resources`, ~15 GB) will
balloon the copy and fill the stick. Instead use an **explicit allow-list** and a **hard `count=` cap**
on every `dd`.

```sh
#!/system/bin/sh
BB=/system/bin/busybox
USB=/mnt/media_rw/udisk1
OUT="$USB/backup_$($BB date +%Y%m%d_%H%M%S)"
BYNAME=/dev/block/platform/mtk-msdc.0/by-name
$BB mkdir -p "$OUT"

# critical small partitions only; 64 MiB hard cap each (none of these exceed it)
for p in preloader lk boot recovery logo para expdb frp nvram nvdata \
         proinfo protect1 protect2 seccfg oemkeystore keystore tee1 tee2 metadata; do
  src="$BYNAME/$p"
  [ -e "$src" ] || continue
  $BB dd if="$src" of="$OUT/$p.img" bs=1048576 count=64 2>>"$OUT/backup.log"
  $BB md5sum "$OUT/$p.img" >> "$OUT/manifest.md5"
  $BB sync
done
# preloader actually lives on the hw-boot partitions:
$BB dd if=/dev/block/mmcblk0boot0 of="$OUT/preloader0.img" bs=1048576 count=8 2>>"$OUT/backup.log"
$BB sync
```

Pull the `backup_*` folder off the stick afterwards. Keep `boot.img` especially — it's your restore
image if you ever do touch the boot chain.

> Reminder from the [hook doc](02-usb-boot-hook.md): the hook runs **once per boot**, so do the whole
> backup in a single `done.sh`; don't plan on a second insert in the same session.
