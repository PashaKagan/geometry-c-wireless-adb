#!/system/bin/sh
# Steering-wheel media-button daemon (reference version).
# Tails the vendor key log and re-injects wheel presses as Android media keys.
# Installed to /system/bin/steering.sh and autostarted from install-recovery.sh.
# Kill switch: touch /data/local/tmp/steering.off
#
# Keycodes observed on the Geometry C (ECARX E01):
#   163 -> next (MEDIA_NEXT 87) | 165 -> prev (MEDIA_PREVIOUS 88) | 267 -> play/pause (MEDIA_PLAY_PAUSE 85)
# If your AA app ignores `input keyevent`, swap the inject line for a media-session
# dispatch (e.g. `cmd media_session dispatch next`) — capture works regardless.

[ -f /data/local/tmp/steering.off ] && exit 0

# small settle so the log pipe is ready at boot
sleep 20
logcat -c 2>/dev/null

logcat -v brief 2>/dev/null | while read line; do
  case "$line" in
    *"keyCodeAction = 1"*)
      case "$line" in
        *"keyCode = 163"*) input keyevent 87 ;;
        *"keyCode = 165"*) input keyevent 88 ;;
        *"keyCode = 267"*) input keyevent 85 ;;
      esac ;;
  esac
  [ -f /data/local/tmp/steering.off ] && exit 0
done
