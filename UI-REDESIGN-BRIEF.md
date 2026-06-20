# Handoff brief (next session)

Read this first. Everything needed to continue is here. (Filename is historical — this
is now the general project handoff, not just the UI pass, which is long done.)

## What this is
- Native **Android, Jetpack Compose (Kotlin)**, single-tablet **offline-first** library manager.
- The library: **"Library of the Sciences of the Qur'an and Sunnah"** (مكتبة علوم الكتاب والسنة) — Islamic/Sharia-sciences charity library in Ethiopia. Built for the user's dad and volunteers.
- Tablet: Samsung **Galaxy Tab A11+ 5G (SM-X236B)**, Android 16, 11" 1920×1200 (portrait in use), connected via USB (`adb`).
- Trilingual: **Amharic (default) / Arabic (RTL) / English**. Dual Ethiopian+Gregorian dates.

## Current state (June 2026)
- **Last released: v1.6.0** (`versionCode 8`, `versionName 1.6.0` in `app/build.gradle.kts`), signed release, git tag **`v1.6.0`**.
- `master` HEAD is **`e38805b`**, pushed to `origin` (private repo `HotSalsa10/EthiopiaLibrary`). **master == the released build** (no unreleased features).
- **126 unit tests green** (Robolectric/JVM). Signed `assembleRelease` OK (R8 + resource shrink).
- The tablet runs the **signed v1.6.0 release**, installed this session. Its local DB is **empty** — the debug→release install required an uninstall, wiping the session's test data (nothing was cloud-backed-up). Real catalog/members to be entered or restored.

## Shipped in v1.6.0 (released this session)
- **Phase A** (`38e23e0`): categories are a pickable, staff-addable list (13 seeded from `CategoriesNamings.txt`); structured book codes **`CAT-000-C-VV`** (category · book# · copy# · volume); books search matches title/author/**code** + category filter chips. Schema redesign.
- **Dad's requests 3/4/5** (`462e198`): members get optional **national ID + address**; **rate a member 1–5 (skippable)** at return (per-loan, shown as average + history stars on member detail); **loan period editable per checkout** (prefilled from the setting).
- **Find-a-copy search** on **checkout** (`07761e5`), **return** (`51f7465`), and the **dashboard overdue list** (`aac7cbc`). Shared `CopyPickerStep` (scan + type name/author/code → status-aware list). Checkout shows all copies (available tappable); return shows only on-loan copies; dashboard filters overdue loans by book or member.

➡️ **All six of dad's original feature requests (1–6) are now done.** (See memory `dad-feature-requests`.)

## ⚠️ Important technical notes
- **DB is at version 3 with `fallbackToDestructiveMigration()`** (`data/LibraryDatabase.kt`). A schema bump currently **wipes all local data**. This was fine pre-production (empty/test catalog), but **before real data is entered, replace the destructive fallback with a proper Room migration.** Schemas are exported to `app/schemas/…/{1,2,3}.json` precisely to support writing migrations.
- **Device install signature:** the tablet now runs the **release**-signed build. `adb install -r` of another **release** build updates it in place (no data loss). A **debug** build will NOT install over it (different key) → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; `adb uninstall com.ethiopialibrary.app` first (wipes data) to side-load a debug build.
- **Tablet DB is empty** as of the v1.6.0 install. The app has no delete-member action (suspend only), so a clean install is the way to fully reset.
- Cloud sync is **one-way backup** to Firestore via `FakeCloudStore`-tested logic; a real Firestore round-trip on the tablet has **not** been confirmed (needs `google-services.json` + sign-in).

## Constraints / decisions (do NOT violate)
- **No dark mode.** Locked to one warm light theme for kiosk consistency (`ui/theme/Theme.kt`). Palette: forest green `#2E5B3E`, gold `#B8902F`, parchment `#F4EEE2`, white cards.
- **Keep bundled Noto fonts** (`res/font/noto_sans_ethiopic`, `noto_naskh_arabic`). One elegant family across all three languages.
- **Trilingual + RTL-safe** (logical start/end, `Icons.AutoMirrored`). Large touch targets (buttons ≥64dp). Material icons (no Lucide).
- **Edit-book must NOT allow changing category** (would break the `CAT-000-C-VV` code scheme).
- **TDD for any logic** (write the failing test first); **UI verified via on-device screenshots.**

## Suggested next steps
1. **Replace the destructive DB migration** with a real Room migration **before any production data is entered** — a schema bump currently wipes data; schemas are exported in `app/schemas/` to write migrations against. Most important pre-production task.
2. On-device: confirm a **real Firestore backup→restore** round-trip (sign-in) and a **QR-scan** with printed labels.
3. Future releases are **release→release**, so they update in place without wiping data (only the one-time debug→release switch wiped it). Bump `versionCode`/`versionName`, `:app:testDebugUnitTest :app:assembleRelease`, `adb install -r`, commit + tag + push.

## Build / deploy / screenshot (toolchain installed)
- Gradle: **`./gradlew.bat`** (downloads gradle 8.9 on first run) — or standalone `$env:LOCALAPPDATA\Gradle\gradle-8.9\bin\gradle.bat`.
- adb: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` (not on PATH).
- Tests: `./gradlew.bat :app:testDebugUnitTest`.
- Fast iterate (debug): `./gradlew.bat :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk` (preserves data; uninstall first only on signature mismatch).
- Keystore + `keystore.properties` in repo root (release signing). Firebase `google-services.json` in `app/`.
- Force a language for screenshots: `adb shell cmd locale set-app-locales com.ethiopialibrary.app --locales en` (reset to `am` when done — default).
- Screenshot: `adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png device-screenshots/x.png`.
- **Driving the UI by tap** (no eyeballing): `adb shell uiautomator dump /sdcard/ui.xml` → pull → regex `text/content-desc … bounds="[x1,y1][x2,y2]"`, tap the center. Type single words; `adb shell input text "A%sB"` for spaces; dismiss keyboard before tapping buttons. **Don't** rely on `keyevent 4` (BACK) to close the keyboard on full-screen search fields — it can exit the screen; tap result rows above the keyboard instead. Overdue/time-travel scenarios can't be made on real hardware — cover with Robolectric. (See memory `on-device-ui-verification`.)

## Key files
- Theme/palette: `ui/theme/Theme.kt` (LightColors + LibraryAccents). Shared UI: `ui/Components.kt` (buttons, top bar, `StarRatingInput`/`StarRatingDisplay`).
- Copy search (shared by checkout/return): `ui/screens/CopyPicker.kt`.
- Screens: `ui/screens/` (Dashboard, Books, BookDetail, Members, MemberDetail, Checkout, Return, Settings, Statistics).
- Data layer: `data/Entities.kt`, `data/Daos.kt`, `data/LibraryRepository.kt`, `data/LibraryDatabase.kt`. Sync: `sync/` (SyncEngine, Mappers, CloudStore).
- ViewModels: `ui/ListViewModels.kt`, `ui/CheckoutViewModel.kt`, `ui/ReturnViewModel.kt`.
