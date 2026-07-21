# Maintainer runbook

Dev-facing operational reference. The operator-facing (trilingual) doc is
`docs/RECOVERY.md` - print that one; keep this one.

Context: one tablet, one non-technical operator, permanently out of the
developer's physical reach after handoff. Everything here exists so a
problem can be diagnosed and fixed **remotely**, without needing the
operator to do anything more technical than reading `docs/RECOVERY.md`.

## Release process

`scripts/release.ps1` (run from the repo root, Windows PowerShell):

```powershell
.\scripts\release.ps1 -NotesEn "Fixes the overdue count on always-on tablets." -NotesAm "..." -NotesAr "..."
```

What it does, in order:
1. Reads `versionCode`/`versionName` from `app/build.gradle.kts`.
2. Fetches the **live** `https://github.com/HotSalsa10/EthiopiaLibrary/releases/latest/download/latest.json`
   (the same URL every tablet reads) and asserts the new `versionCode` is
   actually higher - fails fast if you forgot to bump it.
3. Runs `gradlew test assembleRelease` (full suite + signed, minified,
   resource-shrunk release build).
4. Computes the APK's SHA-256.
5. Publishes a GitHub release tagged `v<versionCode>` containing both the
   signed APK and a freshly generated `latest.json` - **both assets in the
   same release**, so `releases/latest/download/{app-release.apk,
   latest.json}` always resolves to a matching pair.

Before running it: bump `versionCode`/`versionName` in
`app/build.gradle.kts` and commit that first (the script only reads the
working tree, it doesn't commit for you).

Tablets discover new releases via `update/UpdateWorker.kt`: a weekly
periodic check plus an on-demand "Check for update" button in Settings.
`android:versionCode` downgrade protection and same-signature enforcement
are Android's own; this app additionally pins the signing cert's SHA-256
(see below) so it refuses to even *offer* installing an APK that isn't
signed with the real release key, before Android's own checks ever run.

## Keystore

- Location: `keystore/release.keystore` + `keystore.properties` (both
  gitignored - **never commit them**; a prior key was rotated and purged
  from history in 2026-07, see the `.gitignore` comment).
- **Single copy exists, on the dev machine.** Losing it means no future
  update can ever be signed and installed over the existing app - the
  entire self-update mechanism depends on it. Back it up off-machine
  (password manager attachment + private cloud + USB at a second
  location) before the tablet ships; this has not been done yet as of
  this writing.
- Certificate SHA-256 (public - safe to share, it's a fingerprint, not a
  secret; embedded as `PINNED_RELEASE_CERT_SHA256` in
  `update/UpdateManifest.kt`):

  ```
  E3:EE:F8:A3:9B:88:BC:91:88:1F:68:CF:6C:AF:42:60:01:F9:C9:E9:5C:7F:45:8F:05:DC:87:20:16:53:B0:AD
  ```

  Verify a keystore matches with:
  ```powershell
  keytool -list -v -keystore keystore/release.keystore -alias ethiopialibrary
  ```

## Password reset (the real recovery path for "password lost")

There is deliberately no self-service "forgot password" email flow in the
app - `user1@ethiopialibrary.org` is not a real mailbox, so a reset button
would be fake. The real path is the Firebase Admin SDK, run by the
maintainer:

```js
// Node.js, with firebase-admin installed and a service-account key for
// the project (Firebase Console → Project settings → General, for the
// project ID; Project settings → Service accounts to generate a key).
const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });

admin.auth().updateUser("<OPERATOR_UID>", {
  password: "the-new-password",
}).then(() => console.log("password reset"));
```

`<OPERATOR_UID>` is the operator's fixed Firebase Auth UID (Firebase
Console → Authentication → Users - there is only ever the one account) -
Firestore security rules are locked to this exact UID, so it never changes
even across a password reset. Give the operator the new password through a
channel they trust (phone call, not chat), then have them sign back in.

## Remote directives (`remote/directives` Firestore doc)

Config-from-cloud: edited directly in the Firebase Console, never through
the app. Fetched and cached by every tablet after each successful backup
drain (`sync/RemoteDirectives.kt`). A missing or wrong-typed field falls
back to its compiled default rather than crashing anything.

| Field | Type | Default | Effect |
|---|---|---|---|
| `announcement_am` / `_ar` / `_en` | string | absent | Text of a dismissible Dashboard card, per language (falls back am → en → ar if the current UI language's field is blank). |
| `announcementId` | string | absent | Required for the card to show at all; dismissal is remembered per-id, so changing this un-dismisses it for every tablet. |
| `updateManifestUrl` | string | absent (uses `UpdateWorker.DEFAULT_MANIFEST_URL`) | Overrides where `UpdateWorker` fetches `latest.json` from - only needed if the releases repo ever changes. |
| `updateCheckEnabled` | bool | `true` | Kill switch for the entire self-update check. |
| `debouncedBackupEnabled` | bool | `true` | Kill switch for the ~10-minute throttled backup-on-change. The 24h periodic safety-net backup is **not** affected by this. |
| `minSupportedVersionCode` | int | absent (no gate) | Below this, Dashboard shows a persistent non-blocking "please update" banner. |

This collection is intentionally separate from `config` (which `restore()`
writes straight into the tablet's own settings) - mixing the two would let
a Console edit get silently overwritten by a restore, or worse, let a
restore smuggle a stale directive back in.

## Heartbeat, manifest, and Crashlytics - "is the tablet OK?"

Three independent signals, checked in the Firebase Console:

- **`meta/heartbeat`** (Firestore) - written on every successful backup
  drain (~every 10 min while online, daily at minimum via the periodic
  worker): app versionCode/versionName, Android SDK int, per-table row
  counts, count of locale-corrupted codes, and the device's own clock in
  milliseconds. **Stale** (no update in well over a day) means the tablet
  has been offline, powered off, or crash-looping before it can even sync -
  check Crashlytics next. Compare the heartbeat's device-clock field
  against the document's own Firestore server timestamp to spot clock
  drift remotely.
- **`meta/manifest`** - written last in the same batch as the heartbeat;
  its presence certifies the backup that produced it was complete (not
  torn mid-upload). A restore compares against it.
- **Crashlytics** (Firebase Console → Crashlytics) - every caught
  non-fatal exception from `safeLaunch` plus any real crash reports here.
  A crash-loop shows up as repeated events for the same signature; check
  this if heartbeats stop but you suspect the app is still installed and
  running (vs. the tablet being powered off, which heartbeats alone can't
  distinguish from a crash loop).

## Firestore wipe procedure (before go-live)

Before handing the tablet to the operator, wipe accumulated test data -
**but not everything**:

**Wipe** (test/dev junk): `categories`, `books`, `book_copies`, `members`,
`loans`, `config`, `meta` (manifest + heartbeat - stale test-device values).

**Do NOT wipe** `remote` (`remote/directives`) - this is dev-authored
config (announcements, kill switches), not tablet data. Wiping it just
resets every field to its compiled default, which is harmless but pointless
busywork, not a "clean slate" in any meaningful sense.

After wiping, the tablet's first backup repopulates every wiped collection
from the operator's real local data - verify row counts in the next
`meta/manifest` match what Statistics shows on-device.

## Migration floor

`LibraryDatabase` is at schema version 6; `Migrations.kt` implements
`3→4`, `4→5`, and `5→6`. This is deliberate, not a gap: no real deployed
tablet ever ran schema v1 or v2 (those existed only during pre-release
development), so v3 is the actual floor any real backup/restore or
in-place upgrade will ever need to start from. If you ever need to bump the
schema again, the policy in the root `CLAUDE.md` applies: every version
bump ships a `Migration` plus a `MigrationTest` proving old-version data
survives - never re-add a destructive fallback.
