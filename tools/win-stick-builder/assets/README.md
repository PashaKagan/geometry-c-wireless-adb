# assets/ — your own kit (nothing here is redistributed)

The stick builder reads feature files from **this folder on your machine**. Compiled binaries and
APKs are **never committed** to the repo (see `.gitignore`) — you supply your own. The reference
shell scripts ship here.

A feature's checkbox is **greyed out until its asset is present**.

| Feature | Needs in `assets/` | Shipped? | Where to get it |
|---|---|---|---|
| Wireless ADB | — | n/a | pure shell |
| Animation scale | — | n/a | pure shell |
| Read-only backup | — | n/a | pure shell |
| Steering-wheel fix | `steering.sh` | ✅ | shipped (edit if your keycodes differ — see `docs/04`) |
| Seat-preset fix | `seatfix.sh` | ✅ | shipped (`pm clear` the vehicle-settings app, then reboot) |
| Persistent root | `adbd_root`, `geelysu`, `geelysu.allow`, `rootadb.sh` | `rootadb.sh` ✅ | the three binaries/config are **your own compiled kit** (build per `docs/03`); `geelysu.allow` lists your app package names. `su` is made automatically from `geelysu`. |
| Settings shortcut | `Settings_1.0.apk` | ❌ | third-party — get it yourself (see the recommended-apps doc) |
| Extra APKs | `apps/*.apk` | ❌ | your own APKs |

- **`adbd_root`, `geelysu`** are compiled binaries — build them yourself (see `docs/03-rooting.md`),
  not shipped and not reproducible from this repo. The builder installs `geelysu` as both
  `/system/xbin/geelysu` and `/system/xbin/su`.
- **`geelysu.allow`** — one package name per line (the apps allowed to get `su`).
- **`Settings_1.0.apk`** and other third-party APKs are not hosted here — see
  **[docs/09-recommended-apps.md](../../docs/09-recommended-apps.md)**.
