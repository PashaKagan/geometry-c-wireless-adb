# assets/ — the kit the builder installs

The stick builder reads feature files from this folder. The author's own parts — the reference shell
scripts **and the self-compiled binaries** — are committed here, so most toggles work out of the box.
Only **third-party APKs are never committed** (see `.gitignore`); you add those yourself.

A feature's checkbox is **greyed out until its asset is present**.

| Feature | Needs in `assets/` | Shipped? | Notes |
|---|---|---|---|
| Wireless ADB | — | n/a | pure shell |
| Animation scale | — | n/a | pure shell |
| Read-only backup | — | n/a | pure shell |
| Steering-wheel fix | `steering.sh` | ✅ | edit if your keycodes differ (`docs/04`) |
| Seat-preset fix | `seatfix.sh` | ✅ | `pm clear` the vehicle-settings app, then reboot |
| Persistent root | `adbd_root`, `geelysu`, `geelysu.allow`, `rootadb.sh` | ✅ | **binaries are built for one specific firmware** (see below). `su` is made from `geelysu`. |
| Settings shortcut | `Settings_1.0.apk` | ❌ | third-party — add it yourself (`docs/09`) |
| Extra APKs | `apps/*.apk` | ❌ | your own APKs |

> ⚠️ **`adbd_root` is build-specific.** It's a 4-byte patch of *one* unit's stock `adbd`
> (build `SWGE13J…`). On a different firmware build the patch offset differs, so this binary won't
> apply — rebuild per `docs/03-rooting.md`. `geelysu` is an arm64/MT6735 binary (portable within the
> same platform). `geelysu.allow` lists the app package names allowed to get `su` — edit for your apps.
>
> Third-party APKs (e.g. `Settings_1.0.apk`) are not hosted here — see
> **[docs/09-recommended-apps.md](../../docs/09-recommended-apps.md)**.
