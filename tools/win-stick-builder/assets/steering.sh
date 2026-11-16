#!/system/bin/sh
# steering v2 (Geely Geometry C, Israeli firmware). Maps wheel media keys to Android media keys.
# Understands: KeyInput "keyCode163action1" (RU/BY firmware) and PopView "keyCode = 163,keyCodeAction = 1".
# Logs every hard-key line to /data/local/tmp/keys.log. Kill switch: /data/local/tmp/steering.off
export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin
BB=/system/bin/busybox
K=/data/local/tmp/keys.log
[ "$1" = "now" ] || sleep 20
[ -f /data/local/tmp/steering.off ] && exit 0
echo "$(date) steering v2 started pid $$" >> $K; chmod 644 $K
/system/bin/logcat -c
/system/bin/logcat -v time -s KeyInput:* PopView:* | while read line; do
  case "$line" in *eyCode*) ;; *) continue ;; esac
  # size cap ~200 KB
  [ $($BB stat -c %s $K 2>/dev/null || echo 0) -gt 200000 ] && mv -f $K $K.1
  echo "$line" >> $K
  [ -f /data/local/tmp/steering.off ] && continue
  act=""
  case "$line" in
    *"keyCode163action1"*|*"keyCode = 163,keyCodeAction = 1"*|*"keyCode = 163, keyCodeAction = 1"*) act=87 ;;
    *"keyCode165action1"*|*"keyCode = 165,keyCodeAction = 1"*|*"keyCode = 165, keyCodeAction = 1"*) act=88 ;;
    *"keyCode267action1"*|*"keyCode = 267,keyCodeAction = 1"*|*"keyCode = 267, keyCodeAction = 1"*) act=85 ;;
    *"keyCode87action1"*|*"keyCode = 87,keyCodeAction = 1"*)  act=87 ;;
    *"keyCode88action1"*|*"keyCode = 88,keyCodeAction = 1"*)  act=88 ;;
    *"keyCode85action1"*|*"keyCode = 85,keyCodeAction = 1"*)  act=85 ;;
  esac
  if [ -n "$act" ]; then /system/bin/input keyevent $act & echo "  -> injected keyevent $act" >> $K; fi
done
