#!/system/bin/sh
# steering v6 (Geely Geometry C / ECARX "alps E01", Android 5.1).
# Wheel media buttons for projection apps (Android Auto / Open Headunit), with tap vs hold.
#
# The firmware logs every wheel press under logcat tag PopView as:
#   @HardKeyAidlOther@ _keyCode = N, Action = X     (X: 1=down, 2=long-press ~1.5s, 0=release)
# This daemon tails that log and injects Android keys:
#   TAP  (down, quick release)   163 -> NEXT(87)   165 -> PREV(88)   267 -> PLAY/PAUSE(85)
#   HOLD (firmware Action=2)      163 -> FAST-FORWARD(90) repeated   165 -> REWIND(89) repeated
#                                 267 -> VOICE_ASSIST(231): launches the phone assistant (Gemini) in AA
# Hold scrub repeats once/second until release (continuous), capped at 60s as a safety net.
# NOTE: fast-forward/rewind only act in apps that honour MEDIA_FAST_FORWARD/REWIND
#       (VLC and Netflix over AA: yes; YouTube Music ignores them for songs). Tap + assist are universal.
#
# Kill switch: touch /data/local/tmp/steering.off   (exits on the next event)
# Log:         /data/local/tmp/keys.log
trap '' HUP
export PATH=/system/bin:/system/xbin:/sbin:/vendor/bin
K=/data/local/tmp/keys.log
FLAG=/data/local/tmp/steering.scrub      # present while a hold-scrub loop is active
OFF=/data/local/tmp/steering.off
CUR=0; LONG=0
[ "$1" = "now" ] || sleep 20             # let boot settle unless started with "now"
[ -f "$OFF" ] && exit 0
rm -f "$FLAG" 2>/dev/null
echo "$(date) steering v6 started pid $$" >> $K 2>/dev/null; chmod 644 $K 2>/dev/null
logcat -c 2>/dev/null
logcat -v time -s PopView:* 2>/dev/null | while read line; do
  [ -f "$OFF" ] && { rm -f "$FLAG"; exit 0; }
  case "$line" in
    # key down: remember which button, reset long-press flag
    *"_keyCode = 163, Action = 1"*) CUR=163; LONG=0; rm -f "$FLAG" ;;
    *"_keyCode = 165, Action = 1"*) CUR=165; LONG=0; rm -f "$FLAG" ;;
    *"_keyCode = 267, Action = 1"*) CUR=267; LONG=0; rm -f "$FLAG" ;;
    # long-press (~1.5s): start the hold action
    *"_keyCode = 163, Action = 2"*) if [ "$CUR" = 163 ] && [ "$LONG" = 0 ]; then LONG=1; : > "$FLAG"; { n=0; while [ -f "$FLAG" ] && [ $n -lt 60 ]; do input keyevent 90; n=$((n+1)); sleep 1; done; } & fi ;;
    *"_keyCode = 165, Action = 2"*) if [ "$CUR" = 165 ] && [ "$LONG" = 0 ]; then LONG=1; : > "$FLAG"; { n=0; while [ -f "$FLAG" ] && [ $n -lt 60 ]; do input keyevent 89; n=$((n+1)); sleep 1; done; } & fi ;;
    *"_keyCode = 267, Action = 2"*) if [ "$CUR" = 267 ] && [ "$LONG" = 0 ]; then LONG=1; input keyevent 231; echo "$(date) ASSIST" >> $K; fi ;;
    # key up: a quick tap (no long-press) fires the tap action; always stop any scrub
    *"_keyCode = 163, Action = 0"*) rm -f "$FLAG"; [ "$CUR" = 163 ] && [ "$LONG" = 0 ] && input keyevent 87; CUR=0; LONG=0 ;;
    *"_keyCode = 165, Action = 0"*) rm -f "$FLAG"; [ "$CUR" = 165 ] && [ "$LONG" = 0 ] && input keyevent 88; CUR=0; LONG=0 ;;
    *"_keyCode = 267, Action = 0"*) rm -f "$FLAG"; [ "$CUR" = 267 ] && [ "$LONG" = 0 ] && input keyevent 85; CUR=0; LONG=0 ;;
  esac
done
