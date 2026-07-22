# EthiopiaLibrary

Offline-first library management app for a charity library in Ethiopia, built
for library staff and volunteers on cheap Android tablets. Trilingual UI
(Amharic default, Arabic with RTL, English). All data lives locally first
(Room/SQLite with WAL); the app syncs to Firestore in the background when the
tablet has internet, and self-updates over the air from GitHub Releases.

**Start here, depending on what you're doing:**

- **Working on the code (including with Claude Code):** `CLAUDE.md` at the
  repo root is the full project brief — hardware/language constraints, tech
  stack, coding conventions, and the database migration policy. It loads
  automatically in Claude Code sessions.
- **Releasing, recovering a lost password, reading the heartbeat, or
  anything else operational:** `docs/MAINTAINER.md` is the dev runbook.
- **The tablet itself is broken and you're not a developer:** `docs/RECOVERY.md`
  is the trilingual print-and-post guide for the on-site, non-technical
  operator.
- **Old planning notes:** `docs/archive/` — historical only, not current.

## Building

```powershell
.\gradlew.bat testDebugUnitTest   # full test suite (Robolectric/JVM)
.\gradlew.bat assembleDebug       # debug APK
```

## Local secrets (never committed)

Three files are required locally but gitignored and not in this repo — they
live in the team password manager instead:

- `keystore.properties` + `keystore/release.keystore` — release signing key.
  Required only for `assembleRelease` / `scripts/release.ps1`.
- `app/google-services.json` — Firebase config. Required for the app to
  build with sync/Crashlytics enabled; without it, those features compile
  out and everything else (including the full test suite) still works.

See `docs/MAINTAINER.md`'s **Keystore** section for exactly where to place
them and how to verify a keystore is the right one.
