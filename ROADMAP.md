# Roadmap

## Planned: a point-and-click USB-stick builder (Windows)

Today every change means hand-assembling a FAT32 stick (the `b832…` trigger folder + a `done.sh`),
getting the shell quirks right (no `printf`, `echo`-only persistence, correct `/system` remount, one
run per boot), and inserting it in the left port. That's error-prone to do by hand each time.

The idea: a small **Windows app with a checklist of toggles** that generates and writes a ready-to-go
stick for the features you pick, so a non-expert can re-apply everything after a factory reset without
touching shell scripts.

**Proposed toggles**

- [ ] Enable **wireless ADB** (TCP 5555, persistent)
- [ ] Install **persistent root** (patched `adbd` + su daemon)
- [ ] Install the **Settings shortcut** app
- [ ] **Steering-wheel button** fix (log-tailing daemon)
- [ ] **Animation scale** (off / 0.5 / 1.0)
- [ ] **Seat-fix** helper
- [ ] **Read-only partition backup** (dd allow-list to the stick)
- [ ] Bundle your own **extra APKs** to auto-install

**How it would work**

1. Pick a target drive + the toggles.
2. The app formats FAT32, writes the `b832…` trigger folder and inner launcher, and composes a single
   `done.sh` from the selected feature snippets (in a safe order, honouring the once-per-boot rule and
   the `/system` remount/`echo` constraints).
3. Copies in any binaries the chosen features need (patched `adbd`, su daemon, steering daemon,
   Settings APK) and any user-added APKs.
4. You insert the stick in the left port, car on. One pass applies everything.

**Open design questions**

- Ship the prebuilt binaries in the app, or fetch them from this repo's releases?
- A matching "undo" stick that removes the `install-recovery.sh` lines and added files.
- Verify-after-apply: have `done.sh` write a result log to the stick that the app reads back.
- Cross-platform later (the same generator logic would work as a small cross-platform tool).

Contributions/ideas welcome via issues — though note the repo's **no-support** policy.
