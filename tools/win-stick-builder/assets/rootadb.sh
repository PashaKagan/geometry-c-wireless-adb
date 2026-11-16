#!/system/bin/sh
# Root ADB: bind-mounts the patched adbd (privilege drop removed) over /sbin/adbd
# and restarts adbd. Auto-rolls back if adbd does not come back. Started at boot
# from install-recovery.sh (runs as root). Kill switch: create
# /data/local/tmp/rootadb.disable  (or /cache/rootadb.disable).
export PATH=/sbin:/system/sbin:/system/bin:/system/xbin
BB=/system/bin/busybox
L=/data/local/tmp/rootadb.log
log(){ echo "$(date) | $*" >> $L; }
[ "$1" = "now" ] || sleep 25
if [ -f /data/local/tmp/rootadb.disable ] || [ -f /cache/rootadb.disable ]; then log "disabled by flag"; exit 0; fi
[ -x /system/bin/adbd_root ] || { log "adbd_root missing"; exit 0; }
uid_of_adbd(){ $BB ps | $BB grep "/sbin/adbd" | $BB grep -v grep | $BB awk '{print $2}' | $BB head -n 1; }
if [ "$(uid_of_adbd)" = "root" ] || [ "$(uid_of_adbd)" = "0" ]; then log "adbd already root"; exit 0; fi
$BB grep -q " /sbin/adbd " /proc/mounts || mount -o bind /system/bin/adbd_root /sbin/adbd 2>>$L || $BB mount --bind /system/bin/adbd_root /sbin/adbd 2>>$L
if ! $BB grep -q " /sbin/adbd " /proc/mounts; then log "bind mount FAILED"; exit 0; fi
stop adbd; sleep 2; start adbd
i=0; while [ $i -lt 15 ]; do sleep 1; u=$(uid_of_adbd); [ -n "$u" ] && break; i=$((i+1)); done
u=$(uid_of_adbd)
if [ -n "$u" ]; then
  log "adbd restarted, running as: $u"
else
  log "adbd did NOT come back -> rolling back to stock"
  umount /sbin/adbd 2>>$L || $BB umount /sbin/adbd 2>>$L
  stop adbd; sleep 1; start adbd
  log "rollback done; adbd: $(uid_of_adbd)"
fi
chmod 644 $L
