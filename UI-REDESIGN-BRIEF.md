# Handoff: Premium UI Pass

Read this first, then do the premium UI redesign. Everything needed to continue is here.

## Current state (June 2026)
- App: native **Android, Jetpack Compose (Kotlin)**, single-tablet offline-first library manager.
- Live on the tablet: **v1.2.0** (versionCode 4), signed release.
- Tablet: Samsung **Galaxy Tab A11+ 5G (SM-X236B)**, Android 16, 11" 1920×1200, connected via USB (`adb`).
- Already done: full feature set (checkout/return/renew/multi-checkout, books+copies, members, QR scan + labels, dual Ethiopian/Gregorian dates, cloud backup via Firestore, daily snapshots, staff PIN, statistics page, due-soon, borrowing limit). **95 unit tests green.**
- Current theme = the **logo palette**: forest green `#2E5B3E`, gold `#B8902F`, parchment background `#F4EEE2`, white cards. Real logo (`LibraryLogo.jpg`) in the dashboard header; emblem is the launcher icon. App name translated (am/en/ar); Amharic is the default locale.
- The library: **"Library of the Sciences of the Qur'an and Sunnah"** (مكتبة علوم الكتاب والسنة) — Islamic/Sharia-sciences charity library in Ethiopia.

## The redesign goal (adapted from geminiUI.txt — a web prompt translated to our native stack)
Make the UI feel **premium, warm, tactile, expensive** — "minimalist but warm." Keep ALL existing labels/data. Specifics:
- **Buttons**: rich green *gradient* fill, tactile pill shape, **press-scale** feedback (touch device — no hover).
- **Stat cards (focal point)**: large elegant numbers, smaller/subtler icons, a faint **watermark** of the metric icon in the card background, soft shadow depth instead of flat borders, press feedback.
- **Header**: refined title typography; logo + name integrated gracefully; backup status as a small subtle indicator.
- **Structure**: warm neutral grays, hairline separators + soft shadows, **generous padding** (24–32dp), green used *sparingly* as an accent. Secondary nav as a cleaner segmented row.
- Apply the polish across all screens, not just the dashboard.

## Constraints / decisions (do NOT violate)
- **No dark mode.** App is deliberately locked to one warm light theme for kiosk consistency (`Theme.AppCompat.Light.NoActionBar`, painted Surface in `ui/theme/Theme.kt`). Skip the prompt's dark-mode toggle.
- **Keep bundled Noto fonts** (`res/font/noto_sans_ethiopic`, `noto_naskh_arabic`) for Amharic/Arabic. No serif for those scripts. Keep one elegant family across all three languages.
- **Trilingual (am default / ar RTL / en) + RTL-safe** (logical start/end, `Icons.AutoMirrored`). Large touch targets (buttons ≥64dp). Material icons stay (no Lucide).
- TDD for any logic; UI verified via on-device screenshots.

## Pending feature work (separate from the redesign)
- Borrowing-history UI on member/book detail (queries `repo.memberHistory`/`bookHistory` already done + tested).
- Daily overdue notification (POST_NOTIFICATIONS + channel + MaintenanceWorker).
- On-device: cloud-backup sign-in, QR-scan test, optional UAD-NG debloat.

## How to build / deploy / screenshot (toolchain already installed)
- Gradle: `$env:LOCALAPPDATA\Gradle\gradle-8.9\bin\gradle.bat`
- adb: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`
- Keystore + `keystore.properties` in repo root (release signing). Firebase `google-services.json` in `app/`.
- Fast iterate (debug): `gradle :app:assembleDebug`, then `adb uninstall com.ethiopialibrary.app; adb install app/build/outputs/apk/debug/app-debug.apk`
- Force English for screenshots (after launch): `adb shell cmd locale set-app-locales com.ethiopialibrary.app --locales en`
- Screenshot: `adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png device-screenshots/x.png`
- Final: bump version in `app/build.gradle.kts`, `gradle :app:testDebugUnitTest :app:assembleRelease`, install release, commit + push to GitHub (private repo `HotSalsa10/EthiopiaLibrary`).

## Key files
- Theme/palette: `app/src/main/java/com/ethiopialibrary/app/ui/theme/Theme.kt` (LightColors + LibraryAccents)
- Shared buttons/top bar: `app/src/main/java/com/ethiopialibrary/app/ui/Components.kt`
- Dashboard: `app/src/main/java/com/ethiopialibrary/app/ui/screens/DashboardScreen.kt`
- Other screens: `ui/screens/` (Books, BookDetail, Members, MemberDetail, Checkout, Return, Settings, Statistics)
