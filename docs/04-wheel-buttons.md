# 4. Steering-wheel media buttons in projection apps

**The earlier verdict ("can't be done, the buttons go straight to Bluetooth AVRCP and never reach
Android") was wrong.** On this unit the wheel's next / prev / play-pause presses **do** surface, not
as standard Android input events, but as lines in a **vendor key log**. A small root daemon tails that
log and re-injects each press as an Android key, which Android Auto / Open Headunit then honour.

Better still, the firmware log distinguishes a **tap** from a **hold**, so one physical button does
two jobs:

| wheel button | tap | hold (~1.5s, continuous until release) |
|---|---|---|
| forward / next | next track | **fast-forward** (seek) |
| back / prev | previous track | **rewind** (seek) |
| play | play / pause | **launch the phone's voice assistant (Gemini) in Android Auto** |

Volume and mute already work natively and need nothing. This particular wheel has **no dedicated voice
button**, which is why hold-play is mapped to the assistant.

---

## The key insight — a three-state vendor log

The presses don't appear via `getevent` / standard input, which is why remapper apps see nothing. But
in `logcat`, under the tag **`PopView`**, the vendor UI logs every press with an **Action** that tells
you down vs. long-press vs. release:

```
PopView: @HardKeyAidlOther@ _keyCode = 163, Action = 1     <- key down
PopView: @HardKeyAidlOther@ _keyCode = 163, Action = 2     <- long-press fired (held ~1.5s)
PopView: @HardKeyAidlOther@ _keyCode = 163, Action = 0     <- release
```

| Action | meaning |
|---|---|
| **1** | key down |
| **2** | long-press threshold reached (~1.5s), emitted once while still held |
| **0** | release |

That native long-press event is what makes tap-vs-hold reliable: you don't have to time it yourself.

Observed wheel keycodes and the Android keys they map to:

| wheel button | vendor keyCode | tap -> Android key | hold -> Android key |
|---|---|---|---|
| next | **163** | `MEDIA_NEXT` (87) | `MEDIA_FAST_FORWARD` (90), repeated |
| previous | **165** | `MEDIA_PREVIOUS` (88) | `MEDIA_REWIND` (89), repeated |
| play / pause | **267** | `MEDIA_PLAY_PAUSE` (85) | `VOICE_ASSIST` (231) |

(Other Geely/ECARX builds, especially RU/BY firmware, log under `KeyInput` as `keyCode163action1`
with **no** `action2` long-press. On those the taps work but hold has nothing to hook; capture your
own lines from `keys.log` and adapt.)

---

## How the daemon decides tap vs hold

For each button it tracks the current key and whether a long-press has fired:

1. **Action 1 (down):** remember the keycode, clear the long-press flag.
2. **Action 2 (long-press):** set the flag and start the hold action.
   - forward/back: spawn a background loop that injects `FAST_FORWARD`/`REWIND` once a second **until
     release** (continuous scrub), capped at 60s as a safety net.
   - play: fire `VOICE_ASSIST` (231) once — the phone assistant opens in AA.
3. **Action 0 (release):** stop any running scrub loop; if **no** long-press had fired, it was a tap,
   so inject the tap key (next/prev/play-pause).

The continuous scrub is controlled by a flag file (`/data/local/tmp/steering.scrub`): the long-press
creates it, release deletes it, and the background loop runs only while it exists. That keeps hold
responsive and guarantees it stops the instant you let go.

The full script is [`tools/win-stick-builder/assets/steering.sh`](../tools/win-stick-builder/assets/steering.sh)
(v6). It's installed to `/system/bin/steering.sh` and autostarted from `install-recovery.sh` (see
[the hook doc](02-usb-boot-hook.md)), so it survives reboots.

---

## The one caveat — seek is app-dependent

Tap (next/prev/play) and hold-play (assistant) work everywhere. **Hold-to-seek only does something in
apps whose media session honours `MEDIA_FAST_FORWARD` / `MEDIA_REWIND`.** That's the app's call, not
the daemon's — the daemon always injects the key; whether it scrubs is up to what's playing:

- ✅ **VLC** (over Android Auto) — fast-forward and rewind both scrub. **Tested, works perfectly.**
- ✅ **Netflix** (over Android Auto) — **tested, works perfectly.**
- ❌ **YouTube Music** — ignores FF/REW for songs (it's built around skip), so hold does nothing there.
  Tap (skip) and hold-play (assistant) still work.

So if hold-seek seems dead, check the app first: play something in VLC to confirm the daemon is fine,
then accept that skip-only apps simply won't scrub.

---

## Install / permanence / kill switch

- **Install:** tick "Steering-wheel buttons" in the [Windows stick builder](../tools/win-stick-builder/),
  or drop `steering.sh` on a hook stick. It copies to `/system/bin/steering.sh`, adds the autostart
  line to `install-recovery.sh`, and starts it immediately — permanent across reboots.
- **Kill switch:** `touch /data/local/tmp/steering.off` (the daemon exits on the next event). Delete
  the file and restart it to re-enable.
- **Log:** `/data/local/tmp/keys.log` records start lines and hold actions — handy for adapting
  keycodes on a different wheel or firmware.

---

## Community origin

The capture approach began as a port of the `steering2` script that circulates for the related Geely
Atlas on the same ECARX platform. The three-state tap/hold handling, the keycode map, and the
hold-to-seek / hold-to-assistant mappings above are this Geometry C build's own, worked out by
inspecting the `PopView` log.
