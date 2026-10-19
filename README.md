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
| 0 | Repo, CI, signing, release publishing, diagnostics screen | **in progress** |
| 1 | Design system + Continue Watching hero | planned |
| 2 | Favorites, app grid, folders, menus, settings | planned |
| 3 | Home button, start on boot, Hebrew/RTL pass | planned |
| 4 | Performance and polish against the budgets | planned |

Stage 0 shows a diagnostics screen (UI surface size, display mode, density, memory, build) used to size
everything that follows.

## Budgets

| Budget | Target | Enforced |
|---|---|---|
| Release APK | < 6 MB | CI fails above 6 MB |
| Native libraries | none | CI fails if the APK contains any `.so` |
| Memory while visible | < ~150 MB PSS | measured on the TV (debug overlay, stage 2) |
| Frame rate | 60 fps on Mali-G31 | measured on the TV |
| Idle work | none (no polling, services or endless animations) | code review |

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

Kotlin, Jetpack Compose (foundation/ui only), with Coil, OkHttp and org.json arriving in stage 1. No DI
framework, database, Retrofit, Firebase, analytics or ads. minSdk 28, compile/target SDK 37, AGP 9.4,
Gradle 9.8, Kotlin 2.4.

Design tokens and motion rules: see [DESIGN.md](DESIGN.md).

## Credits

- Fonts (from stage 1): [Inter](https://rsms.me/inter/) and [Heebo](https://fonts.google.com/specimen/Heebo),
  both under the SIL Open Font License 1.1.
- Not affiliated with Apple. No Apple fonts, symbols, logos or names are used.
