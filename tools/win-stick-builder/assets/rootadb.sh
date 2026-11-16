#!/system/bin/sh
# Root ADB enabler (reference). Bind-mounts a root-keeping adbd over the live one,
# so wireless ADB connects as uid 0. Needs /system/bin/adbd_root (a patched adbd
# that does NOT drop to the shell user — see docs/03-rooting.md to reproduce it).
# Autostarted from install-recovery.sh. Kill switch: touch /data/local/tmp/rootadb.disable
[ -f /data/local/tmp/rootadb.disable ] && exit 0
[ -x /system/bin/adbd_root ] || exit 0

if [ -e /sbin/adbd ]; then TGT=/sbin/adbd; else TGT=/system/bin/adbd; fi
mount -o bind /system/bin/adbd_root "$TGT" 2>/dev/null
setprop ctl.restart adbd 2>/dev/null

# verify adbd came back; if not, roll the bind back so we never brick ADB
i=0
while [ $i -lt 10 ]; do
  sleep 1
  [ "$(getprop init.svc.adbd)" = "running" ] && exit 0
  i=$((i+1))
done
umount "$TGT" 2>/dev/null
setprop ctl.restart adbd 2>/dev/null
