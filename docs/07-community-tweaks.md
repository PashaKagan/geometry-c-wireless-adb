# 7. Community tweaks & findings

A grab-bag of model-specific tweaks and honest findings, gathered from Geely owner communities
(4pda, Telegram) for this ECARX / MT6735 / Android 5.1 platform and verified against this unit where
noted. Credit to those communities for the originals.

---

## Usable without root

- **Open the hidden stock Settings.** On some trims the full Android Settings is hidden from the
  launcher. A tiny "Settings shortcut" APK (a zero-permission activity that just opens
  `com.android.settings/.Settings`) puts it back — or just `am start -n com.android.settings/.Settings`
  over ADB. Full Settings = Wi‑Fi for wireless AA, DPI/animations, uninstalling apps.
- **Custom / programmable wheel button** (if your trim has one): assignable in the head unit's own
  settings to launch Android Auto, the voice assistant, media controls, or the 360 camera.
- **Install apps without ADB:** the same FAT32 + left-port USB method the community uses (an
  `ES_Install`-style archive) sideloads a file manager, after which you can install any Android-5 APK.
- **Android Auto DPI:** set resolution/DPI to "medium" in the AA settings for a cleaner projection.

## Steering-wheel media buttons

See [doc 4](04-wheel-buttons.md) — the next/prev/play buttons **can** be made to work in projection
apps via a small log-tailing daemon, with **tap vs hold**: tap = next/prev/play-pause, hold
fwd/back = continuous fast-forward/rewind (in apps that honour media seek), hold play = the phone's
voice assistant (Gemini) in Android Auto. (Earlier community wisdom said impossible; it isn't.)

## Audio (engineering menu) — ⚠️ caution

There's an engineering/service menu with **amplifier profile toggles** (standard / Bose / Harman).
**Enabling a profile for an amp you don't have routes audio to a non-existent external amp and kills
all media sound** (beeps still work). Only ever select the profile that matches your actual hardware,
or leave them all off. People have "bricked" their audio this way and needed a shop to undo it.

**Viper4Android** gives a real audio improvement but needs a system driver → either root (see
[doc 3](03-rooting.md)) or a ready flashable installer. Installs to `/system`; your risk.

## Regen auto-set

The regen-braking level resets every start. A **MacroDroid** macro that sets it to "high" on startup
(community `regen2.macro`) automates it. It's an app+macro, not a system tweak.

## Firmware — regional caveat ⚠️

People swap Chinese ↔ Russian GKUI firmware via **SP Flash Tool** (MT6735, BROM — no fastboot). Big
warning: **Russian firmware removes the charging-schedule / SOC-limit menu**, and Chinese-firmware
charging settings lock in after the swap. If you ever swap, set "Charge now" to 100% and **Save**
first. There is no official OTA for these units, and a wrong-region flash can drop local-market
features — don't reflash region firmware without a same-region image.

## OBD2

- ELM327 **with CAN-FD** support; verify CAN-H/CAN-L on pins 6 & 14 (some cheap cables shift pins).
- Standard OBD-II (modes 01/03/09: live data, DTCs, VIN) works; Geely adds Volvo-like extensions.
- Full EV battery data (cell voltages, SOH) generally needs the factory tool, not a generic app.
  Good for a live SOC/temp/DTC dashboard, not deep coding.

## Things that are NOT possible from the head unit

- **OS upgrade** — hard-stuck on Android 5.1; no newer AOSP exists for this board.
- **"Unlocking" vehicle features** (ADAS/HUD/wireless-charge/CarPlay toggles that show in Settings) —
  these are gated by the **body-control-module / vehicle configuration** read over the vehicle bus,
  not by Android. If your trim's config doesn't enable it, it won't appear and can't be switched on in
  head-unit software. (CarPlay is compiled into the firmware but config-gated off on some trims.)
- **Front parking-sensor beep** on trims that ship without it — "not provided" in firmware.
- **More power / range** — nothing to do with the head unit.

## Seat-comfort preset corruption (theory, unverified) — ⚠️ risky

When the seat preset corrupts (otherwise a factory-reset-only fix), a **targeted `pm clear` of the
vehicle-settings app** (`pm clear com.neusoft.optimus.wheeljack.setting`, then reboot) *may* reset that
state. **Not verified here**, and worse: a community report warns it **may wipe ALL of your user
settings**, not just the seat preset. `pm clear` always erases that app's entire state. So treat this
as a last resort only when the seat preset is already broken, expect to redo your other settings, and
make sure you understand what you're clearing first. The stick builder's seat toggle carries the same
warning and is off by default.
