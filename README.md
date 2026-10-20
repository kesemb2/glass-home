# Glass Home

A lightweight, personal home-screen launcher for Android TV with a calm, dark, content-first look and
"liquid glass" surfaces, built for a Homatics Box R 4K Plus (Amlogic S905X4, Mali-G31 MP2, Android TV 14).

Package id: `io.github.kesemb2.glasshome` (fixed forever; changing it would install a different app).

## Install on the TV

The latest build is always at:

```
https://github.com/kesemb2/glass-home/releases/latest/download/glass-home.apk
```

1. Install **Downloader** (by AFTVnews) from the Play Store on the TV.
2. Allow it to install apps: *Settings → Apps → Security & restrictions → Unknown sources → Downloader → On*
   (the exact path differs slightly between Android TV builds).
3. Open Downloader, type the URL above into the address field, press **Go**, then **Install**.
4. Updates: repeat step 3. Every build is signed with the same key, so it installs over the previous one
   and keeps its settings.

## Status

| Stage | Scope | State |
|---|---|---|
| 0 | Repo, CI, signing, release publishing, diagnostics screen | done |
| 1 | Design system + Continue Watching hero | done |
| 2 | Favorites, app grid, folders, menus, settings, live glass | done |
| 3 | Home button, start on boot, Hebrew/RTL pass | done |
| 4 | Performance and polish against the budgets | **in testing** |

Stage 0 measured the box: UI surface 1920 × 1080 px (960 × 540 dp at density 2), video mode 3840 × 2160 @ 59.94 Hz,
33 MB PSS for the minimal screen.

## Continue Watching

The hero reads the system **Watch Next** list (`TvContract.WatchNextPrograms`), the same list Android TV's own home
screen uses. Apps decide what they publish there.

- **Permission:** on first start the app asks to read TV listings (`READ_TV_LISTINGS`, a runtime permission on
  current Android TV builds). Older builds use `READ_EPG_DATA` instead; the app declares both and requests whichever
  this TV treats as a runtime permission. If access is missing, the hero shows what is granted and why.
- **Artwork and text:** the entry's own logo, poster and descriptions first; Nuvio's intent extras; then TMDB
  (lookup by IMDb id or by title, in the TV's language with English fallback). TMDB answers are cached on disk
  for two weeks.
- **Remove from row:** hold OK on Play. Hidden locally only; it comes back if you watch that title again.

## Home screen

- **Favorites** sit on the glass shelf under Continue Watching; **the other apps** are in the grid below (press
  Down). Rows wrap around: Right on the last card goes to the first, and back.
- **Back** goes up one level (a settings page, a menu, a folder); anywhere else on the home screen it returns to
  Continue Watching, like Home.
- **Hold OK** on any app or folder for its menu: move, favorites, folders, hide, app info.
- **Move**: choose Move, then use the arrows; OK or Back when done.
- **Folders**: hold OK on an app → Add to Folder → New Folder (or an existing one). Open a folder with OK.
- **Settings** (gear next to the clock): language (as on the TV, Hebrew or English), glass (live glass, blur,
  refraction, brightness, transparency, reset), app size, logo size, details text size, Play button size, gradient color (black,
  a dark tone of the current image, or a dark blue, purple, green, red, teal or brown; ‹ › on the remote changes
  these), show phone apps, Continue Watching auto-advance, performance overlay, hidden apps (to show them again)
  and About (version, TMDB notice).
- **Continue Watching** shows the title's logo and episode line; the synopsis and time left appear when Play is
  focused.

## Making it the TV's home screen

Settings (gear) › **Home and startup**:

1. **Default home app**: asks the TV to make Glass Home its home app. Where the TV allows it, that is all: Home,
   startup and leaving an app all land in Glass Home.
2. Most Android TV builds do not allow it. Then turn on the **Home helper**: the row opens the TV's accessibility
   settings; choose *Glass Home: Home button*, switch it on and confirm. If the TV says the setting is restricted
   (Android 13+ for apps not installed from the Play Store): TV Settings › Apps › Glass Home › *Allow restricted
   settings*, then try again.
3. With the helper on, **Home button opens Glass Home** and **Replace the TV's home screen** (also right after the
   TV starts and when leaving an app) can each be switched off.
4. **Open the TV's home screen** leaves the helper alone for 10 minutes (or until Home is pressed); **TV settings**
   opens the system settings.

The helper is an accessibility service that sees only the Home key and which activity comes to the front; it does
not read screen content. Pressing Home while Glass Home is open returns to Continue Watching. Back never leaves the
home screen.

## Budgets

| Budget | Target | Enforced |
|---|---|---|
| Release APK | < 6 MB | CI fails above 6 MB (stage 3 build: 2.4 MB) |
| Native libraries | none | CI fails if the APK contains any `.so` |
| Memory while visible | < ~150 MB PSS | Settings → Performance overlay; measured 95–135 MB in stage 2 |
| Frame rate | 60 fps on Mali-G31 | Settings → Performance overlay (fps, slow frames, p95) |
| Idle work | none (no polling, services or endless animations) | code review: only timers are auto-advance (stops after 5 idle minutes), the overlay's own 1 s refresh, and the move-mode wiggle |

The app's own code ships with a baseline profile (`app/src/main/baseline-prof.txt`), so it is compiled ahead of
time on the TV instead of running in the JIT for the first launches.

## How builds work

`.github/workflows/build.yml` runs on every push:

- **Any branch:** unit tests, then a release build (R8 full mode + resource shrinking), budget checks, and the
  APK uploaded as a workflow artifact.
- **`main`:** the same, plus a signed APK published as a GitHub Release named `build-<run number>` and marked
  *latest*, so the download link above always points at it. Only the 10 newest releases are kept.

`versionCode` is the workflow run number and `versionName` is `0.<stage>.<run number>`. Do not rename the
workflow file: run numbers restart per workflow and a lower `versionCode` would not install over the
current build.

### Secrets (GitHub → Settings → Secrets and variables → Actions)

| Secret | Contents |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The release keystore (`glass-home-release.p12`), base64-encoded on one line |
| `SIGNING_KEYSTORE_PASSWORD` | Its password (store and key share it; the key alias is `glasshome`) |
| `TMDB_API_TOKEN` | TMDB API Read Access Token (stage 1) |

Builds on `main` fail if the signing secrets are missing, so an update can never be signed with another key.

### Signing key

- PKCS12 keystore, RSA 4096, alias `glasshome`, valid until 2126.
- Certificate SHA-256: `47:24:D7:B1:30:94:60:33:F0:25:92:24:9E:A2:36:84:31:A4:3D:0E:DF:89:BA:80:C1:1B:12:EF:82:C5:B5:A1`
  (also printed in every release's notes).
- **Keep an offline backup of the keystore file and its password.** If the key is lost, updates can no longer be
  installed over the existing app; it would have to be uninstalled first, losing its settings.

### Building locally (optional)

Requires JDK 21 and an Android SDK with platform 37. Put secrets in the git-ignored `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
tmdb.token=...
signing.storeFile=/path/to/glass-home-release.p12
signing.storePassword=...
```

Then `./gradlew test assembleRelease`. Without signing values the release APK is unsigned.

## Stack

Kotlin, Jetpack Compose (foundation/ui only), Coil 3 (core + OkHttp fetcher), OkHttp, kotlinx-coroutines and the
platform's org.json. No DI framework, database, Retrofit, Firebase, analytics or ads. minSdk 28, compile/target SDK 37, AGP 9.4,
Gradle 9.8, Kotlin 2.4.

Design tokens and motion rules: see [DESIGN.md](DESIGN.md).

## Credits

- Fonts: [Inter](https://rsms.me/inter/) and [Noto Sans Hebrew](https://fonts.google.com/noto/specimen/Noto+Sans+Hebrew),
  both under the SIL Open Font License 1.1 (bundled as subsets).
- Icons: [Material Symbols](https://fonts.google.com/icons), Apache License 2.0.
- Liquid-glass optics (`app/src/main/res/raw/glass_lens.agsl`) adapted from
  [Liquid Glass Android](https://github.com/QWEA0/Liquid-Glass-Android), MIT License, Copyright (c) 2025-2026 pandadog.
- Metadata and artwork: this product uses the TMDB API but is not endorsed or certified by
  [TMDB](https://www.themoviedb.org/). The attribution notice and TMDB logo appear in Settings › About.
- Not affiliated with Apple. No Apple fonts, symbols, logos or names are used.
