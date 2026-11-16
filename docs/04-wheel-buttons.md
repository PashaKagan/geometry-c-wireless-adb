# 4. Steering-wheel media buttons in projection apps

**The earlier verdict ("can't be done — the buttons go straight to Bluetooth AVRCP and never reach
Android") was wrong.** On this unit the wheel's next / prev / play-pause presses **do** surface — not
as standard Android input events, but as lines in a **vendor key log**. A small daemon tails that log
and re-injects each press as an Android media key, which Android Auto / Open Headunit then honour.

This replaces the old workaround (flip the source to Bluetooth Music and back) with a native-feeling
fix.

---

## The key insight

The presses don't appear via `getevent` / standard input, which is why remapper apps see nothing. But
in `logcat` the vendor UI logs them, e.g.:

```
PopView: @HardKeyDispose@ … keyCode = 163, keyCodeAction = 1
```

Observed wheel keycodes on this unit:

| wheel button | vendor keyCode | Android media key |
|---|---|---|
| next track | **163** | `KEYCODE_MEDIA_NEXT` (87) |
| previous track | **165** | `KEYCODE_MEDIA_PREVIOUS` (88) |
| play / pause | **267** | `KEYCODE_MEDIA_PLAY_PAUSE` (85) |

(Volume and mute already work natively and need nothing.) This particular wheel has **no voice
button**; if yours does, capture its keyCode the same way and map it to taste.

---

## The daemon

A root background script (installed to `/system/bin`, autostarted from `install-recovery.sh` — see
[the hook doc](02-usb-boot-hook.md)) that:

1. tails `logcat` for the `@HardKeyDispose@ … keyCode = N, keyCodeAction = 1` lines,
2. debounces (one action per press — the log emits a down and an up),
3. injects the matching media key.

Sketch:

```sh
#!/system/bin/sh
[ -f /data/local/tmp/steering.off ] && exit 0
logcat -c
logcat -v brief | while read line; do
  case "$line" in
    *"keyCodeAction = 1"*)
      case "$line" in
        *"keyCode = 163"*) input keyevent 87 ;;   # next
        *"keyCode = 165"*) input keyevent 88 ;;   # prev
        *"keyCode = 267"*) input keyevent 85 ;;   # play/pause
      esac ;;
  esac
done
```

Kill switch: `touch /data/local/tmp/steering.off` and restart (or reboot).

---

## Injection method — the one thing to verify per app

Which injection call the projection app actually honours can vary:

- `input keyevent 87` (a global media key) — what worked here.
- If your AA app ignores that, try dispatching to the active media session instead, e.g.
  `cmd media_session dispatch next` (naming varies by build), or an `android.intent.action.MEDIA_BUTTON`
  broadcast.

So if the daemon runs but tracks don't skip, the fix is almost always the **injection method**, not
the capture — confirm the log lines appear first (they prove capture works), then swap the inject
call until the app responds.

---

## Community origin

The capture approach is a port of the `steering2` script that circulates for the related Geely Atlas
on the same ECARX platform; this doc is the Geometry C adaptation plus the keycode map above.
