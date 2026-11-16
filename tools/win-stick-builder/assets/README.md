# assets/ — your own kit (nothing here is redistributed)

The stick builder reads feature files from **this folder on your machine**. Binaries and APKs are
**never committed** to the repo (see `.gitignore`) — you supply your own. Only two reference shell
scripts ship here.

A feature's checkbox is **greyed out until its asset is present** here.

| Feature | Needs in `assets/` | Shipped? | Where to get it |
|---|---|---|---|
| Wireless ADB | — | n/a | pure shell, nothing needed |
| Animation scale | — | n/a | pure shell |
| Read-only backup | — | n/a | pure shell |
| Steering-wheel fix | `steering.sh` | ✅ reference shipped | edit if your keycodes differ (see `docs/04`) |
| Persistent root | `adbd_root`, `rootadb.sh`, `geelysu`, `su`, `geelysu.allow` | `rootadb.sh` ✅ only | **your own kit** — build per `docs/03`; `geelysu.allow` lists your app UIDs |
| Settings shortcut | `Settings_1.0.apk` | ❌ | third-party — get it yourself (see links doc) |
| Seat-preset fix | `seatfix.sh` | ❌ | paste the exact command your phone app uses (unverified `pm clear`; understand it first) |
| Extra APKs | `apps/*.apk` | ❌ | drop your own APKs; see the links doc for sources |

- **`adbd_root`, `geelysu`, `su`** are compiled binaries from your private kit — not shipped and not
  reproducible from this repo. `docs/03-rooting.md` documents the method if you want to rebuild them.
- **`Settings_1.0.apk`** and other third-party APKs: this repo does **not** host APKs. See
  **[docs/09-recommended-apps.md](../../docs/09-recommended-apps.md)** for APKPure / Play Store / source links.
- **`seatfix.sh`**: not shipped because the exact command isn't verified here. Paste the one your
  Geely Remote app uses, as a one-line shell script.
