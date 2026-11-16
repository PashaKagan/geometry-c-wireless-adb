# 6. DVR / dashcam footage

**Short version: the recordings are not on the Android head unit, and there is no software route to
them.** This was chased hard (decompiling the DVR app, dumping mounts/partitions, reading the app's
API calls) — the conclusion is architectural, not a missing permission.

---

## What's actually going on

- The dashcam is a **separate camera module / AVM ECU** with its **own microSD** (on this car, tucked
  under the front passenger seat). It records a multi-camera composite to that card.
- The head unit's DVR app (`com.neusoft.cardvr`) is effectively a **remote control + live view**: its
  only real Android APIs are `android.hardware.Camera` (preview) and `Settings.System` get/put (config
  flags). It has **no file, media-player, storage or network APIs** — it doesn't open, read, copy or
  play files at all. Record / play / delete / **format** are all *commands* sent to the module, which
  does the work on its own card.
- Consistently, the unit shows **no second block device** and **nothing mounts** as an external SD
  (even with the DVR app open). That's why a file browser never finds a recordings folder, and why
  **root doesn't help** — the files simply aren't in Android's filesystem to reach.

"But I can format the card from the app!" — yes, because that's a *command* to the module (like a TV
remote telling the TV to wipe its own storage). It isn't the app touching a filesystem.

---

## The `isDvrSupport()` rabbit hole (for the curious)

A framework flag (`WifiService.isDvrSupport()`, `isSupDvr`) reads a vehicle-config byte and can log
`false`, which looks like a "DVR disabled" switch. It isn't the dashcam enable — it gates a different
DVR integration path. The dashcam app itself works regardless. Don't go writing MCU config to "enable
DVR" expecting the under-seat footage to appear on Android; it won't.

---

## Ways to actually get the clips off

1. **microSD extension cable** (the real fix): a flexible microSD-to-microSD extender from the buried
   slot to somewhere reachable (glovebox, console lip). Install once, never crawl again — pop the card
   from the easy end. Standard solution for buried dashcam cards.
2. **Wi‑Fi SD card** (e.g. a FlashAir-class card) in the module's slot, if the module tolerates it —
   pull clips wirelessly.
3. **A bigger card** so you offload far less often.
4. **Screen-record the playback** (last resort): real-time, re-encoded, lossy — only for grabbing one
   clip in a pinch, not archiving. (Note: hardware screen-record on this unit is unreliable and can
   freeze/reboot it.)
